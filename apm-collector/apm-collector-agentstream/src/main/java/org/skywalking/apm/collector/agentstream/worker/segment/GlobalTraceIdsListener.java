package org.skywalking.apm.collector.agentstream.worker.segment;

import org.skywalking.apm.network.proto.UniqueId;

/**
 * @author pengys5
 */
public interface GlobalTraceIdsListener extends SpanListener {
    void parseGlobalTraceId(UniqueId uniqueId);
}
