package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.MemoryPool;
import org.skywalking.apm.network.proto.PoolType;

/**
 * @author wusheng
 */
public abstract class MemoryPoolModule implements MemoryPoolMetricAccessor {
    private List<MemoryPoolMXBean> beans;

    public MemoryPoolModule(List<MemoryPoolMXBean> beans) {
        this.beans = beans;
    }

    @Override
    public List<MemoryPool> getMemoryPoolMetricList() {
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        for (MemoryPoolMXBean bean : beans) {
            String name = bean.getName();
            PoolType type = null;
            if (name.equals(getCodeCacheName())) {
                type = PoolType.CODE_CACHE_USAGE;
            } else if (name.equals(getEdenName())) {
                type = PoolType.NEWGEN_USAGE;
            } else if (name.equals(getOldName())) {
                type = PoolType.OLDGEN_USAGE;
            } else if (name.equals(getSurvivorName())) {
                type = PoolType.SURVIVOR_USAGE;
            } else if (name.equals(getMetaspaceName())) {
                type = PoolType.METASPACE_USAGE;
            } else if (name.equals(getPermName())) {
                type = PoolType.PERMGEN_USAGE;
            }

            if (type != null) {
                MemoryUsage usage = bean.getUsage();
                poolList.add(MemoryPool.newBuilder().setType(type)
                    .setInit(usage.getInit())
                    .setMax(usage.getMax())
                    .setCommited(usage.getCommitted())
                    .setUsed(usage.getUsed())
                    .build());
            }
        }
        return poolList;
    }

    protected abstract String getPermName();

    protected abstract String getCodeCacheName();

    protected abstract String getEdenName();

    protected abstract String getOldName();

    protected abstract String getSurvivorName();

    protected abstract String getMetaspaceName();
}
