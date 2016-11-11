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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public class CSVCollector extends AbstractCollectorBase {

    public CSVCollector(File file) {
        if (!file.getName().endsWith(".csv")) {
            throw new IllegalArgumentException("Illegal file name " + file.getAbsolutePath());
        }

        List<String> headerList = new ArrayList<>();
        headerList.add(FULL_FILE_PATH_COLUMN);
        headerList.add(SHORT_FILE_NAME_COLUMN);
        for (Measure measure : Measure.values()) {
            headerList.add(measure.columnSamples());
            headerList.add(measure.columnMin());
            headerList.add(measure.columnMax());
            headerList.add(measure.columnMean());
            headerList.add(measure.columnStandardDeviation());
            headerList.add(measure.columnMedian());
            headerList.add(measure.column75Percentile());
        }
        String[] header = headerList.toArray(new String[0]);

        try {
            Appendable output = Files.newBufferedWriter(file.toPath());
            this.csvOutput = CSVFormat.DEFAULT.withHeader(header).print(output);
            this.csvOutput.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error accessing CSV file", e);
        }
    }

    public void onFinish(String id) {

        List<Object> record = new ArrayList<>();
        record.add(id);
        record.add(Paths.get(id).getFileName());
        for (Measure m : Measure.values()) {
            if (!results.containsKey(m)) {
                throw new RuntimeException("Measurement is missing " + m);
            }

            DescriptiveStatistics stats = results.get(m);
            record.add(stats.getN());
            record.add(stats.getMin());
            record.add(stats.getMax());
            record.add(stats.getMean());
            record.add(stats.getStandardDeviation());
            record.add(stats.getPercentile(50));
            record.add(stats.getPercentile(75));
        }

        try {
            csvOutput.printRecord(record);
            csvOutput.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write data", e);
        }
    }

    @Override
    public void close() throws IOException {
        csvOutput.close();
    }

    public static final String FULL_FILE_PATH_COLUMN = "File";

    public static final String SHORT_FILE_NAME_COLUMN = "Name";

    private final CSVPrinter csvOutput;
}
