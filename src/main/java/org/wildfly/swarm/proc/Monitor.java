package org.wildfly.swarm.proc;

import java.io.File;
import java.util.Properties;
import java.util.UUID;

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

    public static void main(String[] args) throws Exception {

        if(args.length==0) {
            System.out.println("Usage: Monitor <base-dir> [<output>]");
            System.exit(-1);

        }
        String baseDir = args[0];
        System.out.println("Working on base dir"+ baseDir);

        // test criteria
        Properties props = new Properties();
        props.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-apps.properties"));

        long total0 = System.currentTimeMillis();
        Collector collector = args.length>1 ? new CSVCollector(new File(args[1])) : new SystemOutCollector();

        // main test execution loop
        for (Object o : props.keySet()) {
            String swarmFile = (String) o;
            String httpCheck = (String) props.get(o);

            File file = new File(baseDir + swarmFile);
            String id = file.getAbsolutePath();

            if(!file.exists())
                throw new RuntimeException("File does not exist: "+ file.getAbsolutePath());

            collector.onBegin(id);
            for(int i=0;i<NUM_ITERATIONS; i++) {
                runTest(file, httpCheck, collector);
            }
            collector.onFinish(id);
        }

        System.out.println("Total Exceution Time: "+(System.currentTimeMillis()-total0));
    }

    private static void runTest(File file, String httpCheck, final Collector collector) {

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
    private static void procInfo(String id, String uid, Collector collector) throws Exception {
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
}
