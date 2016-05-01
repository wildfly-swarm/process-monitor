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
