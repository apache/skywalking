package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class ParallelCollectorModule extends MemoryPoolModule {

    public ParallelCollectorModule(List<MemoryPoolMXBean> beans) {
        super(beans);
    }

    @Override protected String getPermName() {
        return "PS Perm Gen";
    }

    @Override protected String getCodeCacheName() {
        return "Code Cache";
    }

    @Override protected String getEdenName() {
        return "PS Eden Space";
    }

    @Override protected String getOldName() {
        return "PS Old Gen";
    }

    @Override protected String getSurvivorName() {
        return "PS Survivor Space";
    }

    @Override protected String getMetaspaceName() {
        return "Metaspace";
    }
}
