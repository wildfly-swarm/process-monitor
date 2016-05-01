package org.wildfly.swarm.proc;

/**
 * @author Heiko Braun
 * @since 29/04/16
 */
interface Collector {
    void onBegin(String id);
    void onMeasurement(String id, Measure measure, double val);
    void onFinish(String id);
}