package org.wildfly.swarm.proc;

import java.util.List;

import org.apache.commons.csv.CSVRecord;

/**
 * @author Heiko Braun
 * @since 02/05/16
 */
public class FailFastComparator implements DeviationComparator {

    private final double threshold;

    public FailFastComparator(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void compare(List<CSVRecord> previous, List<CSVRecord> current) throws ThresholdExceeded {
        boolean skipedFirst = false;
        for (CSVRecord prevRecord : previous) {
            if(!skipedFirst) { // CSV headers
                skipedFirst = true;
                continue;
            }
            String fileName = prevRecord.get(CSVCollector.FILE_NAME_IDX);
            CSVRecord matching = findMatching(fileName, current);
            if(matching!=null) {
                double prevVal = Double.valueOf(prevRecord.get(CSVCollector.STARTUP_PERCENTILE_IDX));
                double currVal = Double.valueOf(matching.get(CSVCollector.STARTUP_PERCENTILE_IDX));

                if(currVal>prevVal) {

                    double limit = (prevVal / 100) * threshold;
                    double diff = currVal-prevVal;
                    double increasePercentage = currVal*100/prevVal;
                    boolean failed = increasePercentage-threshold > 100;
                    if(failed) {
                        throw new ThresholdExceeded(Measure.STARTUP_TIME + " exceeded by "+Math.floor(increasePercentage-100)+"%: "+prevVal+"/"+currVal);
                    }

                }
            }
            else {
                System.out.println("No matching record for test "+fileName +". Skipping ...");
            }
        }
    }
}
