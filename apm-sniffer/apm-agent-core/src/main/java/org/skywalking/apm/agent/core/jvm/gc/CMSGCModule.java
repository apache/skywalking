package org.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class CMSGCModule extends GCModule {
    public CMSGCModule(List<GarbageCollectorMXBean> beans) {
        super(beans);
    }

    @Override protected String getOldGCName() {
        return "ConcurrentMarkSweep";
    }

    @Override protected String getNewGCName() {
        return "ParNew";
    }
}
