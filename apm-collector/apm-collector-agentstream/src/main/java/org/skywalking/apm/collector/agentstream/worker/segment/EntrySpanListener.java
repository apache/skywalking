package org.skywalking.apm.collector.agentstream.worker.segment;

import org.skywalking.apm.network.proto.SpanObject;

/**
 * @author pengys5
 */
public interface EntrySpanListener extends SpanListener {
    void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId);
}
