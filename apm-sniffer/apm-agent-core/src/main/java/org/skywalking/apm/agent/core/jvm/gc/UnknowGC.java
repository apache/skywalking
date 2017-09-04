package org.skywalking.apm.agent.core.jvm.gc;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.GCPhrase;

/**
 * @author wusheng
 */
public class UnknowGC implements GCMetricAccessor {
    @Override
    public List<GC> getGCList() {
        List<GC> gcList = new LinkedList<GC>();
        gcList.add(GC.newBuilder().setPhrase(GCPhrase.NEW).build());
        gcList.add(GC.newBuilder().setPhrase(GCPhrase.OLD).build());
        return gcList;
    }
}
