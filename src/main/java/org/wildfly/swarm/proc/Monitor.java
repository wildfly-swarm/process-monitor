package org.wildfly.swarm.proc;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import com.github.zafarkhaja.semver.Version;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hyperic.sigar.ProcMem;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.ptql.ProcessFinder;

/**
 * @author Heiko Braun
 * @since 28/04/16
 */
public class Monitor {

    public Monitor(File baseDir, File archiveDir, Optional<File> outputFile, Collector collector) {

        this.baseDir = baseDir;
        this.archiveDir = archiveDir;
        this.outputFile = outputFile;
        this.collector = collector;
    }

    public static void main(String[] args) throws Exception {

        if(args.length<2) {
            System.out.println("Usage: Monitor <base-dir> <archive-dir> [<output>]");
            System.exit(-1);

        }
        File baseDir = new File(args[0]);
        File archiveDir = new File(args[1]);

        Optional<File> output = args.length>2 ? Optional.of(new File(args[2])) : Optional.empty();

        System.out.println("Base dir: "+ baseDir.getAbsolutePath());
        System.out.println("Archive dir: "+ archiveDir.getAbsolutePath());

        if(!archiveDir.exists())
            throw new RuntimeException("Archive does not exist: "+archiveDir.getAbsolutePath());

        Collector collector = output.isPresent() ?
                new CSVCollector(output.get()) : new SystemOutCollector();

        // perform tests
        new Monitor(baseDir, archiveDir, output, collector).run();

    }

    private void run() throws Exception {
        long total0 = System.currentTimeMillis();

        // test criteria
        Properties props = new Properties();
        props.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-apps.properties"));

        // first phase: main test execution loop
        for (Object o : props.keySet()) {
            String swarmFile = (String) o;
            String httpCheck = (String) props.get(o);

            File file = new File(this.baseDir, swarmFile);
            String id = file.getAbsolutePath();

            if(!file.exists())
                throw new RuntimeException("File does not exist: "+ file.getAbsolutePath());

            collector.onBegin(id);
            for(int i=0;i<NUM_ITERATIONS; i++) {
                runTest(file, httpCheck, collector);
            }
            collector.onFinish(id);
        }

        System.out.println("Test Execution Time: "+(System.currentTimeMillis()-total0));

        // second phase: compare with previous, archived results
        Optional<ArchivedResult> prev = getPreviousResults(this.archiveDir);
        if(prev.isPresent() && (collector instanceof CSVCollector )) { // limited to CSV files
            checkDeviation(this.outputFile.get(), prev.get());
        }
        else {
            System.out.println("Performance comparison skipped.");
        }
    }

    private void checkDeviation(File testResult, ArchivedResult archivedResult) throws Exception {
        System.out.println("Comparing against " + archivedResult.getVersion());

        List<CSVRecord> current = loadCSV(testResult).getRecords();
        List<CSVRecord> previous = loadCSV(archivedResult.getFile()).getRecords();

        new FailFastComparator(10.00).compare(previous, current);
    }


    private CSVParser loadCSV(File file) throws Exception {
        Reader in = new FileReader(file);
        CSVParser parser = CSVFormat.DEFAULT.parse(in);
        return parser;
    }

    private Optional<ArchivedResult> getPreviousResults(File dir) {

        String[] archivedResults = dir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".csv");
            }
        });

        Map<Version, File> versions = new HashMap<Version, File>();
        for (String archivedResult : archivedResults) {
            File archive = new File(dir, archivedResult);
            String name = archive.getName();
            Version v = Version.valueOf(name.substring(0, name.lastIndexOf(".")));
            versions.put(v, archive);
        }

        Optional<ArchivedResult> res = Optional.empty();

        if(versions.size()>0) {
            List<Version> sortedKeys = new ArrayList<Version>(versions.keySet());
            Collections.sort(sortedKeys, new Comparator<Version>() {
                public int compare(Version v1, Version v2) {
                    return v1.compareTo(v2);
                }
            });

            sortedKeys.forEach( k -> System.out.println(k));

            Version key = sortedKeys.get(sortedKeys.size() - 1);
            res = Optional.of(
                    new ArchivedResult(key, versions.get(key))
            );
        }

        return res;
    }

    private void runTest(File file, String httpCheck, final Collector collector) {

        System.out.println("Testing "+file.getAbsolutePath());
        String id = file.getAbsolutePath();

        String uid = UUID.randomUUID().toString();
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", file.getAbsolutePath(), "-Duid="+ uid, "-d64", "-Xms512m").inheritIO();
        Process process = null;
        boolean escape = false;
        int attempts = 0;

        try {

            final long s0 = System.currentTimeMillis();
            process = pb.start();

            final CloseableHttpClient httpClient = HttpClients.createDefault();

            do {

                try {

                    HttpGet request = new HttpGet(httpCheck);

                    CloseableHttpResponse response = httpClient.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(statusCode!=200) {
                        new RuntimeException("Failed to execute HTTP check: " + statusCode).printStackTrace();
                        escape = true;
                    }

                    procInfo(id, uid, collector);

                    collector.onMeasurement(id, Measure.STARTUP_TIME, new Double(System.currentTimeMillis()-s0));
                    escape = true;
                } catch (HttpHostConnectException e) {

                    //System.err.println(e.getMessage());

                    if(attempts < NUM_CONNECTION_ATTEMPTS) {
                        System.err.println("Failed to connect. Scheduling retry ...");
                        Thread.sleep(MS_BETWEEN_ATTEMPTS);
                        attempts++;
                    }
                    else {
                        System.out.println("Max attempts reached, escaping sequence");
                        escape = true;
                    }
                }
            } while(!escape);

            httpClient.close();
            process.destroy();

        } catch (Throwable t) {
            t.printStackTrace();
        }
        finally {
            if(process!=null && process.isAlive())
                process.destroyForcibly();
        }

    }

    /**
     * See https://support.hyperic.com/display/SIGAR/PTQL
     * @param process
     * @param file
     * @throws Exception
     */
    private void procInfo(String id, String uid, Collector collector) throws Exception {
        Sigar sigar = new Sigar();
        final ProcessFinder processFinder = new ProcessFinder(sigar);
        long pid = processFinder.findSingleProcess("State.Name.eq=java,Args.3.ct="+uid);

        ProcMem procMem = sigar.getProcMem(pid);
        String heapString = Sigar.formatSize(procMem.getResident());
        System.out.println("PID for test driver: "+ pid);
        System.out.println("MEM for PID: "+ heapString);
        collector.onMeasurement(id, Measure.HEAP_AFTER_INVOCATION, Long.valueOf(heapString.substring(0, heapString.length()-1)));  // TODO only works for MB
    }

    private static final int NUM_CONNECTION_ATTEMPTS = 200;

    private static final int MS_BETWEEN_ATTEMPTS = 100;

    private static final int NUM_ITERATIONS = 10;

    private final File baseDir;

    private final File archiveDir;

    private final Optional<File> outputFile;

    private final Collector collector;

    class ArchivedResult {
        private Version version;
        private File file;

        public ArchivedResult(Version version, File file) {
            this.version = version;
            this.file = file;
        }

        public Version getVersion() {
            return version;
        }

        public File getFile() {
            return file;
        }
    }
}
