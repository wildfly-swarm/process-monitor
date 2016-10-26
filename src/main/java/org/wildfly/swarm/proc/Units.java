package org.wildfly.swarm.proc;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Units {
    public static double bytesToMegabytes(long bytes) {
        double mb = (double) bytes / (1024 * 1024);
        return new BigDecimal(mb).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
