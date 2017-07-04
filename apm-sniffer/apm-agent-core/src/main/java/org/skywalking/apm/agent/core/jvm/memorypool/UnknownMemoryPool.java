package org.skywalking.apm.agent.core.jvm.memorypool;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.MemoryPool;
import org.skywalking.apm.network.proto.PoolType;

/**
 * @author wusheng
 */
public class UnknownMemoryPool implements MemoryPoolMetricAccessor {
    @Override
    public List<MemoryPool> getMemoryPoolMetricList() {
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        poolList.add(MemoryPool.newBuilder().setType(PoolType.CODE_CACHE_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.NEWGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.OLDGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.SURVIVOR_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.PERMGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.METASPACE_USAGE).build());
        return poolList;
    }
}
