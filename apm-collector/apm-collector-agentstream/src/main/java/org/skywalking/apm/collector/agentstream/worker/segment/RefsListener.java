package org.skywalking.apm.collector.agentstream.worker.segment;

import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * @author pengys5
 */
public interface RefsListener extends SpanListener {
    void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId);
}
