package org.skywalking.apm.agent.core.jvm.cpu;

import org.skywalking.apm.network.proto.CPU;

/**
 * @author wusheng
 */
public abstract class CPUMetricAccessor {
    private long lastCPUTimeNs;
    private long lastSampleTimeNs;
    private final int cpuCoreNum;

    public CPUMetricAccessor(int cpuCoreNum) {
        this.cpuCoreNum = cpuCoreNum;
    }

    protected void init() {
        lastCPUTimeNs = this.getCpuTime();
        this.lastSampleTimeNs = System.nanoTime();
    }

    protected abstract long getCpuTime();

    public CPU getCPUMetric() {
        long cpuTime = this.getCpuTime();
        long cpuCost = cpuTime - lastCPUTimeNs;
        long now = System.nanoTime();

        CPU.Builder cpuBuilder = CPU.newBuilder();
        return cpuBuilder.setUsagePercent(cpuCost * 1.0d / ((now - lastSampleTimeNs) * cpuCoreNum)).build();
    }
}
