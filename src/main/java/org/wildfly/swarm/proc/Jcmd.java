package org.wildfly.swarm.proc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Triggers diagnostic commands on a JVM using {@code jcmd}.
 */
final class Jcmd {
    private static final String GC = "GC.run";

    public static void gc(long pid) throws Exception {
        perform(pid, GC);
    }

    private static void perform(long pid, String command) throws Exception {
        Process process = null;
        BufferedReader reader = null;

        try {
            ProcessBuilder builder = new ProcessBuilder("jcmd", "" + pid, command);
            builder.environment().put("LC_ALL", "c");
            process = builder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            while (reader.readLine() != null) {
                // read all the output
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }

    }
}
