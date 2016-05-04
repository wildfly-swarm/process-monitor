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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public abstract class AbstractCollectorBase implements Collector {

    protected Map<Measure, DescriptiveStatistics> results = new HashMap<Measure, DescriptiveStatistics>();

    public void onBegin(String id) {

        results.clear();
    }

    public void onMeasurement(String id, Measure measure, double val) {
        if(!results.containsKey(measure))
            results.put(measure, new DescriptiveStatistics());
        results.get(measure).addValue(val);
    }

    public abstract void onFinish(String id);
};
