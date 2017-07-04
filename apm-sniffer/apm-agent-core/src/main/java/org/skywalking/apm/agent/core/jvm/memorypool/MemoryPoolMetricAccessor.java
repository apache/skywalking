package org.skywalking.apm.agent.core.jvm.memorypool;

import java.util.List;
import org.skywalking.apm.network.proto.MemoryPool;

/**
 * @author wusheng
 */
public interface MemoryPoolMetricAccessor {
    List<MemoryPool> getMemoryPoolMetricList();
}
