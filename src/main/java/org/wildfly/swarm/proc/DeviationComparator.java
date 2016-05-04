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
