package org.skywalking.apm.agent.core.jvm.cpu;

import org.skywalking.apm.agent.core.os.ProcessorUtil;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.network.proto.CPU;

/**
 * @author wusheng
 */
public enum CPUProvider {
    INSTANCE;
    private CPUMetricAccessor cpuMetricAccessor;

    CPUProvider() {
        int processorNum = ProcessorUtil.getNumberOfProcessors();
        try {
            CPUProvider.class.getClassLoader().loadClass("com.sun.management.OperatingSystemMXBean");
            this.cpuMetricAccessor = new SunCpuAccessor(processorNum);
        } catch (Exception e) {
            this.cpuMetricAccessor = new NoSupportedCPUAccessor(processorNum);
            ILog logger = LogManager.getLogger(CPUProvider.class);
            logger.error(e, "Only support accessing CPU metric in SUN JVM platform.");
        }
    }

    public CPU getCpuMetric() {
        return cpuMetricAccessor.getCPUMetric();
    }
}
