package org.wildfly.swarm.proc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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

    interface Collector {
        void onBegin(String id);
        void onMeasurement(String id, double val);
        void onFinish(String id);
    }

    public static void main(String[] args) throws Exception {

        if(args.length==0) {
            System.out.println("Usage: Monitor <swarm-jar>");
            System.exit(-1);

        }
        String baseDir = args[0];
        System.out.println("Working on base dir"+ baseDir);

        Properties props = new Properties();
        props.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-apps.properties"));

        Collector collector = new Collector() {

            Map<String,DescriptiveStatistics> results = new HashMap<String, DescriptiveStatistics>();

            public void onBegin(String id) {
                if(!results.containsKey(id))
                    results.put(id, new DescriptiveStatistics());

                results.get(id).clear();
            }

            public void onMeasurement(String id, double val) {
                results.get(id).addValue(val);
            }

            public void onFinish(String id) {
                DescriptiveStatistics stats = results.get(id);

                System.out.println("Results for "+id);
                System.out.println("Measurements: "+stats.getValues().length);
                System.out.println("min: "+stats.getMin());
                System.out.println("max: "+stats.getMax());
                System.out.println("75p: "+stats.getPercentile(75));
            }
        };

        long s0 = System.currentTimeMillis();
        for (Object o : props.keySet()) {
            String swarmFile = (String) o;
            String httpCheck = (String) props.get(o);
            collector.onBegin(swarmFile);
            for(int i=0;i<NUM_ITERATIONS; i++) {
                runTest(baseDir, swarmFile, httpCheck, collector);
            }
            collector.onFinish(swarmFile);
        }

        System.out.println("Total Exceution Time: "+(System.currentTimeMillis()-s0));
    }

    private static void runTest(String baseDir, String swarmFile, String httpCheck, final Collector collector) {

        File file = new File(baseDir + swarmFile);
        if(!file.exists())
            throw new RuntimeException("File does not exist: "+ file.getAbsolutePath());

        System.out.println("----");
        System.out.println("Testing "+file.getAbsolutePath());

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
                    System.out.println("----");
                    System.out.println("Status: " + response.getStatusLine().getStatusCode());
                    collector.onMeasurement(swarmFile, new Double(System.currentTimeMillis()-s0));
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

    private static final int NUM_CONNECTION_ATTEMPTS = 5;

    private static final int MS_BETWEEN_ATTEMPTS = 1000;

    private static final int NUM_ITERATIONS = 5;
}
