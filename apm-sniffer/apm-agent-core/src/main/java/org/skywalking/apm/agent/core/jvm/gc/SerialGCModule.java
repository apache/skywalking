package org.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class SerialGCModule extends GCModule {
    public SerialGCModule(List<GarbageCollectorMXBean> beans) {
        super(beans);
    }

    @Override protected String getOldGCName() {
        return "MarkSweepCompact";
    }

    @Override protected String getNewGCName() {
        return "Copy";
    }
}
