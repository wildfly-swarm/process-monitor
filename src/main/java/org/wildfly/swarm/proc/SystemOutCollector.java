package org.wildfly.swarm.proc;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public class SystemOutCollector extends AbstractCollectorBase {

    public void onFinish(String id) {
        DescriptiveStatistics stats = results.get(id);

        System.out.println("Results for "+id);
        System.out.println("Measurements: "+stats.getValues().length);
        System.out.println("min: "+stats.getMin());
        System.out.println("max: "+stats.getMax());
        System.out.println("75p: "+stats.getPercentile(75));
    }
}
