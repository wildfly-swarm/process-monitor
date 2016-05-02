package org.wildfly.swarm.proc;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public class CSVCollector extends AbstractCollectorBase {

    private final static String SEP = ",";
    private final static String NEWLINE = "\n";

    public CSVCollector(File file) {

        if(!file.getName().endsWith(".csv"))
            throw new IllegalArgumentException("Illegale file name "+file.getAbsolutePath());

        this.file = file;

        try {
            if(!file.exists())
            {
                writeHeader(file);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error access CSV file", e);
        }
    }

    private void writeHeader(File file) throws Exception {

        StringBuffer sb = new StringBuffer();
        sb.append("File").append(SEP);
        sb.append("Name").append(SEP);

        int i=0;
        for (Measure m : Measure.values()) {
            sb.append(m.getShortName()+ SAMPLES).append(SEP);
            sb.append(m.getShortName()+ MIN).append(SEP);
            sb.append(m.getShortName()+ MAX).append(SEP);
            sb.append(m.getShortName()+ PERCENTILE);

            if(i<Measure.values().length-1)
                sb.append(SEP);
            i++;
        }

        PrintWriter writer = new PrintWriter(file, "UTF-8");
        writer.println(sb.toString());
        writer.close();
    }

    public void onFinish(String id) {

        StringBuffer sb = new StringBuffer();
        sb.append(id).append(SEP);
        sb.append(id.substring(id.lastIndexOf("/")+1, id.length())).append(SEP);

        int i=0;
        for (Measure m : Measure.values()) {
            if(!results.containsKey(m))
                throw new RuntimeException("Measurement is missing "+m);

            DescriptiveStatistics stats = results.get(m);
            sb.append(stats.getValues().length).append(SEP);
            sb.append(stats.getMin()).append(SEP);
            sb.append(stats.getMax()).append(SEP);
            sb.append(stats.getPercentile(75));

            if(i<Measure.values().length-1)
                sb.append(SEP);
            i++;
        }
        sb.append(NEWLINE);

        try {
            Files.write(Paths.get(file.getAbsolutePath()), sb.toString().getBytes(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new RuntimeException("Faile to write data", e);
        }
    }

    public static final String SAMPLES = " Samples";

    public static final String MIN = " Min";

    public static final String MAX = " Max";

    public static final String PERCENTILE = " .75";

    public static int STARTUP_PERCENTILE_IDX = 5;
    public static int MEM_PERCENTILE_IDX = 9;
    public static int FILE_NAME_IDX = 1;

    private final File file;
}

