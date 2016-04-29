package org.wildfly.swarm.proc;

import java.io.File;
import java.util.Properties;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

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

        ProcessBuilder pb = new ProcessBuilder("java", "-jar", file.getAbsolutePath()).inheritIO();
        Process process = null;
        boolean escape = false;
        int attempts = 0;

        try {

            final long s0 = System.currentTimeMillis();
            process = pb.start();

            do {

                try {
                    CloseableHttpClient httpClient = HttpClients.createDefault();
                    HttpGet request = new HttpGet(httpCheck);

                    CloseableHttpResponse response = httpClient.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(statusCode!=200) {
                        new RuntimeException("Failed to execute HTTP check: " + statusCode).printStackTrace();
                        escape = true;
                    }

                    collector.onMeasurement(id, new Double(System.currentTimeMillis()-s0));
                    escape = true;
                } catch (HttpHostConnectException e) {

                    System.err.println(e.getMessage());

                    if(attempts < NUM_CONNECTION_ATTEMPTS) {
                        System.err.println("Schedule retry ...");
                        Thread.sleep(MS_BETWEEN_ATTEMPTS);
                        attempts++;
                    }
                    else {
                        System.out.println("Max attempts reached, escaping sequence");
                        escape = true;
                    }
                }
            } while(!escape);

            process.destroy();

        } catch (Throwable t) {
            t.printStackTrace();
        }
        finally {
            if(process!=null && process.isAlive())
                process.destroyForcibly();
        }

    }

    private static final int NUM_CONNECTION_ATTEMPTS = 8;

    private static final int MS_BETWEEN_ATTEMPTS = 1500;

    private static final int NUM_ITERATIONS = 10;
}
