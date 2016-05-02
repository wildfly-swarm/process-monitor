package org.wildfly.swarm.proc;

import java.util.List;

import org.apache.commons.csv.CSVRecord;

/**
 * @author Heiko Braun
 * @since 02/05/16
 */
public interface DeviationComparator {

    void compare(List<CSVRecord> previous, List<CSVRecord> current) throws ThresholdExceeded;

    default CSVRecord findMatching(String seeked, List<CSVRecord> current) {
        CSVRecord match = null;
        for (CSVRecord record : current) {
            String fileName = record.get(CSVCollector.FILE_NAME_IDX);
            if(fileName.equals(seeked)) {
                match = record;
                break;
            }

        }
        return match;
    }

}
