package org.wildfly.swarm.proc;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
public abstract class AbstractCollectorBase implements Collector {

    protected Map<String,DescriptiveStatistics> results = new HashMap<String, DescriptiveStatistics>();

    public void onBegin(String id) {
        if(!results.containsKey(id))
            results.put(id, new DescriptiveStatistics());

        results.get(id).clear();
    }

    public void onMeasurement(String id, double val) {
        results.get(id).addValue(val);
    }

    public abstract void onFinish(String id);
};
