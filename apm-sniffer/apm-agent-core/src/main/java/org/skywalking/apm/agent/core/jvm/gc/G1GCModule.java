package org.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

/**
 * @author wusheng
 */
public class G1GCModule extends GCModule {
    public G1GCModule(List<GarbageCollectorMXBean> beans) {
        super(beans);
    }

    @Override protected String getOldGCName() {
        return "G1 Old Generation";
    }

    @Override protected String getNewGCName() {
        return "G1 Young Generation";
    }
}
