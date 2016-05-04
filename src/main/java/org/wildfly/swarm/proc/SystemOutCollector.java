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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public class SystemOutCollector extends AbstractCollectorBase {

    public void onFinish(String id) {
        System.out.println("Results for "+ id);
        for (Measure m : results.keySet()) {
            DescriptiveStatistics stats = results.get(m);
            System.out.println(m.name()+" Samples: "+stats.getValues().length);
            System.out.println(m.name()+" min: "+stats.getMin());
            System.out.println(m.name()+" max: "+stats.getMax());
            System.out.println(m.name()+" 75p: "+stats.getPercentile(75));
        }

    }
}
