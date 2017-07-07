package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class SerialCollectorModule extends MemoryPoolModule {
    public SerialCollectorModule(List<MemoryPoolMXBean> beans) {
        super(beans);
    }

    @Override protected String getPermName() {
        return "Perm Gen";
    }

    @Override protected String getCodeCacheName() {
        return "Code Cache";
    }

    @Override protected String getEdenName() {
        return "Eden Space";
    }

    @Override protected String getOldName() {
        return "Tenured Gen";
    }

    @Override protected String getSurvivorName() {
        return "Survivor Space";
    }

    @Override protected String getMetaspaceName() {
        return "Metaspace";
    }
}
