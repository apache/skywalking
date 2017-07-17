package org.skywalking.apm.agent.core.jvm.gc;

import java.util.List;
import org.skywalking.apm.network.proto.GC;

/**
 * @author wusheng
 */
public interface GCMetricAccessor {
    List<GC> getGCList();
}
