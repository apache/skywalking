package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class G1CollectorModule extends MemoryPoolModule {
    public G1CollectorModule(List<MemoryPoolMXBean> beans) {
        super(beans);
    }

    @Override protected String getPermName() {
        return "G1 Perm Gen";
    }

    @Override protected String getCodeCacheName() {
        return "Code Cache";
    }

    @Override protected String getEdenName() {
        return "G1 Eden Space";
    }

    @Override protected String getOldName() {
        return "G1 Old Gen";
    }

    @Override protected String getSurvivorName() {
        return "G1 Survivor Space";
    }

    @Override protected String getMetaspaceName() {
        return "Metaspace";
    }
}
