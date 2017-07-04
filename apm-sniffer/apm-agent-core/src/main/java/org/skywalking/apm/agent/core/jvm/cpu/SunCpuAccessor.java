package org.skywalking.apm.agent.core.jvm.cpu;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/**
 * @author wusheng
 */
public class SunCpuAccessor extends CPUMetricAccessor {
    private final OperatingSystemMXBean osMBean;

    public SunCpuAccessor(int cpuCoreNum) {
        super(cpuCoreNum);
        this.osMBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        this.init();
    }

    @Override
    protected long getCpuTime() {
        return osMBean.getProcessCpuTime();
    }
}
