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
            throw new IllegalArgumentException("Illegal file name "+file.getAbsolutePath());

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

        StringBuilder sb = new StringBuilder();
        sb.append("File").append(SEP);
        sb.append("Name").append(SEP);

        int i=0;
        for (Measure m : Measure.values()) {
            sb.append(m.getShortName()).append(SAMPLES).append(SEP);
            sb.append(m.getShortName()).append(MIN).append(SEP);
            sb.append(m.getShortName()).append(MAX).append(SEP);
            sb.append(m.getShortName()).append(PERCENTILE);

            if(i<Measure.values().length-1)
                sb.append(SEP);
            i++;
        }

        PrintWriter writer = new PrintWriter(file, "UTF-8");
        writer.println(sb.toString());
        writer.close();
    }

    public void onFinish(String id) {

        StringBuilder sb = new StringBuilder();
        sb.append(id).append(SEP);
        sb.append(id.substring(id.lastIndexOf("/")+1, id.length())).append(SEP);

        int i=0;
        for (Measure m : Measure.values()) {
            if(!results.containsKey(m))
                throw new RuntimeException("Measurement is missing "+m);

            DescriptiveStatistics stats = results.get(m);
            sb.append(stats.getN()).append(SEP);
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
            throw new RuntimeException("Failed to write data", e);
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

