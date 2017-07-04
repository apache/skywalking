package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;
import org.skywalking.apm.network.proto.MemoryPool;

/**
 * @author wusheng
 */
public enum MemoryPoolProvider {
    INSTANCE;

    private MemoryPoolMetricAccessor metricAccessor;
    private List<MemoryPoolMXBean> beans;

    MemoryPoolProvider() {
        beans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean bean : beans) {
            String name = bean.getName();
            MemoryPoolMetricAccessor accessor = findByBeanName(name);
            if (accessor != null) {
                metricAccessor = accessor;
                break;
            }
        }
        if (metricAccessor == null) {
            metricAccessor = new UnknownMemoryPool();
        }
    }

    public List<MemoryPool> getMemoryPoolMetricList() {
        return metricAccessor.getMemoryPoolMetricList();
    }

    private MemoryPoolMetricAccessor findByBeanName(String name) {
        if (name.indexOf("PS") > -1) {
            //Parallel (Old) collector ( -XX:+UseParallelOldGC )
            return new ParallelCollectorModule(beans);
        } else if (name.indexOf("CMS") > -1) {
            // CMS collector ( -XX:+UseConcMarkSweepGC )
            return null;
        } else if (name.indexOf("G1") > -1) {
            // G1 collector ( -XX:+UseG1GC )
            return null;
        } else if (name.equals("Survivor Space")) {
            // Serial collector ( -XX:+UseSerialGC )
            return new SerialCollectorModule(beans);
        } else {
            // Unknown
            return null;
        }
    }
}
