package org.wildfly.swarm.proc;

import java.io.File;
import java.io.PrintWriter;

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
        sb.append("Measurement").append(SEP);
        sb.append("Min").append(SEP);
        sb.append("Max").append(SEP);
        sb.append(".75");
        sb.append(NEWLINE);

        PrintWriter writer = new PrintWriter(file, "UTF-8");
        writer.println(sb.toString());
        writer.close();
    }

    public void onFinish(String id) {

        DescriptiveStatistics stats = results.get(id);

        StringBuffer sb = new StringBuffer();
        sb.append(id).append(SEP);
        sb.append(stats.getValues().length).append(SEP);
        sb.append(stats.getMin()).append(SEP);
        sb.append(stats.getMax()).append(SEP);
        sb.append(stats.getPercentile(75));
        sb.append(NEWLINE);

        try {
            PrintWriter writer = new PrintWriter(file, "UTF-8");
            writer.println(sb.toString());
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("Faile to write data", e);
        }
    }

    private final File file;
}

