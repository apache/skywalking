package org.skywalking.apm.agent.core.os;

import java.lang.management.ManagementFactory;

/**
 * @author wusheng
 */
public class ProcessorUtil {
    public static int getNumberOfProcessors() {
        return ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
    }
}
