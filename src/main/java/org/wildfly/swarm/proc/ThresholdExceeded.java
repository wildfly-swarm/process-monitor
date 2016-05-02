package org.wildfly.swarm.proc;

/**
 * @author Heiko Braun
 * @since 02/05/16
 */
public class ThresholdExceeded extends Exception {
    public ThresholdExceeded(String message) {
        super(message);
    }
}
