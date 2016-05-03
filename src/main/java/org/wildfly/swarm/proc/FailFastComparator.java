package org.wildfly.swarm.proc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;

/**
 * @author Heiko Braun
 * @since 02/05/16
 */
public class FailFastComparator implements DeviationComparator {

    private final double threshold;

    private final Map<Measure, Integer> criteria = new HashMap<>();

    public FailFastComparator(double threshold) {
        this.threshold = threshold;

        criteria.put(Measure.HEAP_AFTER_INVOCATION, CSVCollector.MEM_PERCENTILE_IDX);
        criteria.put(Measure.STARTUP_TIME, CSVCollector.STARTUP_PERCENTILE_IDX);
    }

    @Override
    public void compare(List<CSVRecord> previous, List<CSVRecord> current) throws ThresholdExceeded {
        boolean skipedFirst = false;
        List<ComparisonResult> comparisonResults = new ArrayList<>();
        int maxChars = 0;
        for (CSVRecord prevRecord : previous) {
            if(!skipedFirst) { // CSV headers
                skipedFirst = true;
                continue;
            }
            String fileName = prevRecord.get(CSVCollector.FILE_NAME_IDX);
            if(fileName.length()>maxChars) maxChars = fileName.length();
            CSVRecord matching = findMatching(fileName, current);
            if(matching!=null) {

                for (Measure measure : criteria.keySet()) {
                    int idx = criteria.get(measure);
                    double prevVal = Double.valueOf(prevRecord.get(idx));
                    double currVal = Double.valueOf(matching.get(idx));

                    if(currVal>prevVal) {

                        double increasePercentage = currVal*100/prevVal;
                        boolean failed = increasePercentage-threshold > 100;
                        String message = StringUtils.rightPad(measure.getShortName(),10) + " +"+Math.floor(increasePercentage-100)+"% ("+prevVal+"/"+currVal+")";
                        comparisonResults.add(
                                new ComparisonResult(measure, fileName, failed, message)
                        );
                    }
                    else {
                        double decreasePercentage = prevVal*100/currVal;
                        String message = StringUtils.rightPad(measure.getShortName(),10) +" -"+Math.floor(decreasePercentage-100) + "% ("+ prevVal+">"+currVal+")";

                        comparisonResults.add(
                                new ComparisonResult(measure, fileName, message)
                        );
                    }
                }

            }
            else {
                System.err.println("No matching record for test "+fileName +". Skipping ...");
            }
        }

        // dump results
        final int pad = maxChars+2;
        Collections.sort(comparisonResults, new Comparator<ComparisonResult>() {
            @Override
            public int compare(ComparisonResult o1, ComparisonResult o2) {
                return o1.getMeasure().compareTo(o2.getMeasure());
            }
        });

        comparisonResults.forEach(r -> System.out.println(StringUtils.rightPad(r.getFile(), pad)+": "+r.getMessage()));

        // decide if ThresholdExceeded
        List<ComparisonResult> failedTests = comparisonResults.stream().filter(r -> !r.isFailure()).collect(Collectors.toList());
        if(failedTests.size()>0) {
            System.err.println("There have been test errors. See previous logs for details ...");
            throw new ThresholdExceeded(failedTests.size() + " test(s) did exceed the "+this.threshold+"% tolerance.");
        }

    }

    class ComparisonResult {
        private String file;
        private boolean failed;
        private String message;
        private Measure measure;

        public ComparisonResult(Measure measure, String file, String message) {
            this(measure, file, false, message);
        }

        public ComparisonResult(Measure measure, String file, boolean failed, String message) {
            this.measure = measure;
            this.file = file;
            this.failed = failed;
            this.message = message;
        }

        public Measure getMeasure() {
            return measure;
        }

        public String getFile() {
            return file;
        }

        public boolean isFailure() {
            return failed;
        }

        public String getMessage() {
            return message;
        }
    }
}
