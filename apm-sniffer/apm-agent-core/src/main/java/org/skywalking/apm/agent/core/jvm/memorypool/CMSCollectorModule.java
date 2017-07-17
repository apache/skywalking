package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class CMSCollectorModule extends MemoryPoolModule {
    public CMSCollectorModule(List<MemoryPoolMXBean> beans) {
        super(beans);
    }

    @Override protected String getPermName() {
        return "CMS Perm Gen";
    }

    @Override protected String getCodeCacheName() {
        return "Code Cache";
    }

    @Override protected String getEdenName() {
        return "Par Eden Space";
    }

    @Override protected String getOldName() {
        return "CMS Old Gen";
    }

    @Override protected String getSurvivorName() {
        return "Par Survivor Space";
    }

    @Override protected String getMetaspaceName() {
        return "Metaspace";
    }
}
