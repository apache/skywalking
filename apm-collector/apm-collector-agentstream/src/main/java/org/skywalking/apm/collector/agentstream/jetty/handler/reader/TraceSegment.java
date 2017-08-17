package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UniqueId;

/**
 * @author pengys5
 */
public class TraceSegment {

    private List<UniqueId> uniqueIds;
    private TraceSegmentObject traceSegmentObject;

    public TraceSegment() {
        uniqueIds = new ArrayList<>();
    }

    public List<UniqueId> getGlobalTraceIds() {
        return uniqueIds;
    }

    public void addGlobalTraceId(UniqueId globalTraceId) {
        uniqueIds.add(globalTraceId);
    }

    public TraceSegmentObject getTraceSegmentObject() {
        return traceSegmentObject;
    }

    public void setTraceSegmentObject(TraceSegmentObject traceSegmentObject) {
        this.traceSegmentObject = traceSegmentObject;
    }
}
