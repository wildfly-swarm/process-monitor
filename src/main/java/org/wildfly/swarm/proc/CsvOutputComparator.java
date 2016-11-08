package org.wildfly.swarm.proc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class CsvOutputComparator implements DeviationComparator {
    private final File csvFile;

    private final String previousName;

    private final String currentName;

    public CsvOutputComparator(File csvFile, String previousName, String currentName) {
        this.csvFile = csvFile;
        this.previousName = previousName;
        this.currentName = currentName;
    }

    @Override
    public void compare(List<CSVRecord> previous, List<CSVRecord> current) throws IOException {
        List<String> headerList = new ArrayList<>();
        headerList.add("Version");
        headerList.add("Name");
        for (Measure measure : Measure.values()) {
            headerList.add(measure.getShortName() + CSVCollector.MIN);
            headerList.add(measure.getShortName() + CSVCollector.MAX);
            headerList.add(measure.getShortName() + CSVCollector.MEAN);
            headerList.add(measure.getShortName() + CSVCollector.STANDARD_DEVIATION);
            headerList.add(measure.getShortName() + CSVCollector.MEDIAN);
            headerList.add(measure.getShortName() + CSVCollector.PERCENTILE_75);
        }
        String[] header = headerList.toArray(new String[0]);

        Appendable output = Files.newBufferedWriter(csvFile.toPath());
        try (CSVPrinter csv = CSVFormat.DEFAULT.withHeader(header).print(output)) {
            for (CSVRecord recordInPrevious : previous) {
                String fileName = recordInPrevious.get(CSVCollector.FILE_NAME_IDX);
                CSVRecord recordInCurrent = findMatching(fileName, current);

                if (recordInCurrent != null) {
                    printRecord(csv, previousName, fileName, recordInPrevious);
                    printRecord(csv, currentName, fileName, recordInCurrent);
                }
            }
        }
    }

    private void printRecord(CSVPrinter csv, String version, String fileName, CSVRecord record) throws IOException {
        List<Object> values = new ArrayList<>();

        values.add(version);
        values.add(fileName);
        for (Measure measure : Measure.values()) {
            values.add(record.get(measure.getShortName() + CSVCollector.MIN));
            values.add(record.get(measure.getShortName() + CSVCollector.MAX));
            values.add(record.get(measure.getShortName() + CSVCollector.MEAN));
            values.add(record.get(measure.getShortName() + CSVCollector.STANDARD_DEVIATION));
            values.add(record.get(measure.getShortName() + CSVCollector.MEDIAN));
            values.add(record.get(measure.getShortName() + CSVCollector.PERCENTILE_75));
        }

        csv.printRecord(values);
    }

}
