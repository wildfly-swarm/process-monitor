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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;

/**
 * @author Heiko Braun
 * @since 02/05/16
 */
public class FailFastComparator implements DeviationComparator {

    private final double threshold;

    private final Set<Measure> criteria = new HashSet<>();

    public FailFastComparator(double threshold) {
        this.threshold = threshold;

        criteria.add(Measure.RSS_AFTER_INVOCATION);
        criteria.add(Measure.STARTUP_TIME);
        criteria.add(Measure.JAR_SIZE);
    }

    @Override
    public void compare(List<CSVRecord> previous, List<CSVRecord> current) throws ThresholdExceeded {
        List<ComparisonResult> comparisonResults = new ArrayList<>();
        int maxChars = 0;
        for (CSVRecord prevRecord : previous) {
            String fileName = prevRecord.get(CSVCollector.SHORT_FILE_NAME_COLUMN);
            if(fileName.length()>maxChars) maxChars = fileName.length();
            CSVRecord matching = findMatching(fileName, current);
            if(matching!=null) {

                for (Measure measure : criteria) {
                    double prevVal = Double.valueOf(prevRecord.get(measure.column75Percentile()));
                    double currVal = Double.valueOf(matching.get(measure.column75Percentile()));

                    if(currVal>prevVal) {

                        double increasePercentage = currVal*100/prevVal;
                        boolean failed = increasePercentage-threshold > 100;
                        String message = StringUtils.rightPad(measure.getShortName(),10) + " +"+Math.floor(increasePercentage-100)+"% ("+prevVal+" -> "+currVal+")";
                        comparisonResults.add(
                                new ComparisonResult(measure, fileName, failed, message)
                        );
                    }
                    else {
                        double decreasePercentage = prevVal*100/currVal;
                        String message = StringUtils.rightPad(measure.getShortName(),10) +" -"+Math.floor(decreasePercentage-100) + "% ("+ prevVal+" -> "+currVal+")";

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
        Collections.sort(comparisonResults, Comparator.comparing(ComparisonResult::getMeasure));

        comparisonResults.forEach(r -> System.out.println(StringUtils.rightPad(r.getFile(), pad)+": "+r.getMessage()));

        // decide if ThresholdExceeded
        List<ComparisonResult> failedTests = comparisonResults.stream().filter(r -> r.isFailure()).collect(Collectors.toList());
        if(failedTests.size()>0) {
            System.err.println("There have been test errors. See previous logs for details ...");
            throw new ThresholdExceeded(failedTests.size() + " test(s) did exceed the "+this.threshold+"% tolerance.");
        }

    }

    static class ComparisonResult {
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
