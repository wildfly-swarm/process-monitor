/*
 * *
 *  * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.swarm.proc;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.zafarkhaja.semver.Version;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

    public Monitor(CommandLine cmd) {

        baseDir = new File(cmd.getOptionValue("b"));
        archiveDir = cmd.hasOption("a") ? Optional.of(new File(cmd.getOptionValue("a"))) : Optional.empty();

        outputFile = cmd.hasOption("o") ? Optional.of(new File(cmd.getOptionValue("o"))) : Optional.empty();

        System.out.println("Base dir: "+ baseDir.getAbsolutePath());

        if(archiveDir.isPresent())
            System.out.println("Archive dir: "+ archiveDir.get().getAbsolutePath());

        if(archiveDir.isPresent() && !archiveDir.get().exists())
            throw new RuntimeException("Archive does not exist: "+archiveDir.get().getAbsolutePath());

        collector = outputFile.isPresent() ?
                new CSVCollector(outputFile.get()) : new SystemOutCollector();


        this.NUM_ITERATIONS = cmd.hasOption("n") ? Integer.valueOf(cmd.getOptionValue("n")) : 10;
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption(
                OptionBuilder
                        .withLongOpt("base")
                        .isRequired(true)
                        .withDescription("the WildFly Swarm examples directory")
                        .hasArg()
                        .create("b")
        );

        options.addOption(
                OptionBuilder
                        .withLongOpt("archive")
                        .isRequired(false)
                        .withDescription("the directory with previous performance results")
                        .hasArg()
                        .create("a")
        );


        options.addOption(
                OptionBuilder
                        .withLongOpt("output")
                        .isRequired(false)
                        .withDescription("the .csv file to store the current test results")
                        .hasArg()
                        .create("o")
        );

        options.addOption(
                        OptionBuilder
                                .withLongOpt("skip-tests")
                                .isRequired(false)
                                .withDescription("skip test exection phase")
                                .create("skip")
                );

        options.addOption(
                OptionBuilder
                        .withLongOpt("number-iterations")
                        .isRequired(false)
                        .hasArg()
                        .withDescription("number of iterations per test")
                        .create("n")
        );

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            usage(options);
        }

        for(Option opt : options.getOptions())
        {
            if(opt.isRequired() && !cmd.hasOption(opt.getOpt()))
            {
                usage(options);
            }
        }

        // perform tests
        new Monitor(cmd)
                .skipTests(cmd.hasOption("skip"))
                .run();

    }

    private Monitor skipTests(boolean b) {
        this.skipTests = b;
        return this;
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Monitor", "WildFly Swarm Performance Monitor", options, "", true);
        System.exit(-1);
    }

    private void run() throws Exception {
        long total0 = System.currentTimeMillis();

        if(!skipTests) {
            // test criteria
            Properties props = new Properties();
            props.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-apps.properties"));

            // first phase: main test execution loop
            for (Object o : props.keySet()) {
                String swarmFile = (String) o;
                String httpCheck = (String) props.get(o);

                File file = new File(this.baseDir, swarmFile);
                String id = file.getAbsolutePath();

                if (!file.exists())
                    throw new RuntimeException("File does not exist: " + file.getAbsolutePath());

                collector.onBegin(id);
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    runTest(file, httpCheck, collector);
                }
                collector.onFinish(id);
            }

            System.out.println("Test Execution Time: " + (System.currentTimeMillis() - total0));

        }
        else {
            System.out.println("Test execution has been skipped.");
        }


        // second phase: compare with previous, archived results
        if(outputFile.isPresent() && archiveDir.isPresent()) {
            Optional<ArchivedResult> prev = getPreviousResults(this.archiveDir.get());
            if (prev.isPresent() && (collector instanceof CSVCollector)) { // limited to CSV files
                checkDeviation(this.outputFile.get(), prev.get());
            } else {
                System.out.println("Performance comparison skipped.");
            }
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

    /**
     * Main test execution. Spawns an external process
     * @param file
     * @param httpCheck
     * @param collector
     */
    private void runTest(File file, String httpCheck, final Collector collector) {

        System.out.println("Testing "+file.getAbsolutePath());
        String id = file.getAbsolutePath();

        String uid = UUID.randomUUID().toString();
        ProcessBuilder pb = new ProcessBuilder("java", "-Duid="+ uid, "-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"), "-jar", file.getAbsolutePath()).inheritIO();
        Process process = null;
        int attempts = 0;

        try {

            final long s0 = System.currentTimeMillis();
            process = pb.start();

            final CloseableHttpClient httpClient = HttpClients.createDefault();

            while (true) {
                if (attempts >= NUM_CONNECTION_ATTEMPTS) {
                    System.out.println("Max attempts reached, escaping sequence");
                    break;
                }

                CloseableHttpResponse response = null;
                try {
                    HttpGet request = new HttpGet(httpCheck);
                    response = httpClient.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 200) {
                        collector.onMeasurement(id, Measure.STARTUP_TIME, (double) (System.currentTimeMillis() - s0));
                        procInfo(id, uid, collector);
                        break;
                    } else if (statusCode == 404) {
                        // this can happen during server boot, when the HTTP endpoint is already exposed
                        // but the application is not yet deployed
                    } else {
                        System.err.println("Failed to execute HTTP check: " + statusCode);
                        break;
                    }
                } catch (HttpHostConnectException e) {
                    // server not running yet
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }

                attempts++;
                Thread.sleep(MS_BETWEEN_ATTEMPTS);
            }

            httpClient.close();
            process.destroy();
            process.waitFor(2000, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (process!=null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
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
        long pid = processFinder.findSingleProcess("State.Name.eq=java,Args.1.ct="+uid);

        ProcMem procMem = sigar.getProcMem(pid);
        String heapString = Sigar.formatSize(procMem.getResident());
        System.out.println("PID for test driver: "+ pid);
        System.out.println("MEM for PID: "+ heapString);
        collector.onMeasurement(id, Measure.HEAP_AFTER_INVOCATION, Long.valueOf(heapString.substring(0, heapString.length()-1)));  // TODO only works for MB
    }

    private static final int NUM_CONNECTION_ATTEMPTS = 200;

    private static final int MS_BETWEEN_ATTEMPTS = 100;

    private int NUM_ITERATIONS = 10;

    private final File baseDir;

    private final Optional<File> archiveDir;

    private final Optional<File> outputFile;

    private final Collector collector;

    private boolean skipTests;


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
