package org.wildfly.swarm.proc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Measures the amount of used Java heap using {@code jstat}. Assumes OpenJDK 8 or derivatives, which should be fine.
 */
final class Jstat {
    private static final String[] RELEVANT_JSTAT_OUTPUT_COLUMNS = {
            "S0U", // used survivor space 0
            "S1U", // used survivor space 1; note that one of S0U and S1U is always 0
            "EU",  // used eden space
            "OU",  // used old space
            // common tools (jconsole, jvisualvm, jmc) report these as non-heap memory
            //"MU",  // used metaspace
            //"CCSU" // used compressed class space; note that it's actually part of metaspace, so computing MU + CCSU is wrong
    };

    /**
     * @return size of used Java heap (survivor spaces + eden + tenured + metaspace + ccspace), in bytes
     * (precision is actually less, but that shouldn't matter)
     */
    public static long usedHeap(long pid) throws Exception {
        Process process = null;
        BufferedReader reader = null;

        try {
            ProcessBuilder builder = new ProcessBuilder("jstat", "-gc", "" + pid);
            builder.environment().put("LC_ALL", "c");
            process = builder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String firstLine = reader.readLine();
            if (firstLine == null) {
                firstLine = "";
            }
            String secondLine = reader.readLine();
            if (secondLine == null) {
                secondLine = "";
            }

            // trim needed, the first line starts with whitespace!
            String[] names = firstLine.trim().split("\\s+");
            String[] values = secondLine.trim().split("\\s+");

            double result = 0;
            for (String column : RELEVANT_JSTAT_OUTPUT_COLUMNS) {
                int index = arrayIndex(names, column);
                if (index >= 0) {
                    result += Double.parseDouble(values[index]);
                }
            }

            // jstat prints output in kB
            return (long) (result * 1024);
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }

    }

    private static int arrayIndex(String[] array, String item) {
        for (int i = 0; i < array.length; i++) {
            if (item.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }
}
