package org.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class ParallelGCModule extends GCModule {
    public ParallelGCModule(List<GarbageCollectorMXBean> beans) {
        super(beans);
    }

    @Override protected String getOldGCName() {
        return "PS MarkSweep";
    }

    @Override protected String getNewGCName() {
        return "PS Scavenge";
    }
}
