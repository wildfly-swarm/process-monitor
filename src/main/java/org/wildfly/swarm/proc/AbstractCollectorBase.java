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
