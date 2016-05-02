package org.wildfly.swarm.proc;

/**
 * @author Heiko Braun
 * @since 01/05/16
 */
public enum Measure {

    STARTUP_TIME("start"),
    HEAP_AFTER_INVOCATION("mem");

    Measure(String shortName) {
        this.shortName = shortName;
    }

    public String getShortName() {
        return shortName;
    }

    private final String shortName;
}
