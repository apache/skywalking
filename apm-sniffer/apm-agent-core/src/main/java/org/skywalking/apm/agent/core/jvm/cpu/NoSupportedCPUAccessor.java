package org.skywalking.apm.agent.core.jvm.cpu;

/**
 * @author wusheng
 */
public class NoSupportedCPUAccessor extends CPUMetricAccessor {
    public NoSupportedCPUAccessor(int cpuCoreNum) {
        super(cpuCoreNum);
    }

    @Override
    protected long getCpuTime() {
        return 0;
    }
}
