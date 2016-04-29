package org.wildfly.swarm.proc;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public class SystemOutCollector  implements Collector {

    Map<String,DescriptiveStatistics> results = new HashMap<String, DescriptiveStatistics>();

    public void onBegin(String id) {
        if(!results.containsKey(id))
            results.put(id, new DescriptiveStatistics());

        results.get(id).clear();
    }

    public void onMeasurement(String id, double val) {
        results.get(id).addValue(val);
    }

    public void onFinish(String id) {
        DescriptiveStatistics stats = results.get(id);

        System.out.println("Results for "+id);
        System.out.println("Measurements: "+stats.getValues().length);
        System.out.println("min: "+stats.getMin());
        System.out.println("max: "+stats.getMax());
        System.out.println("75p: "+stats.getPercentile(75));
    }
};
