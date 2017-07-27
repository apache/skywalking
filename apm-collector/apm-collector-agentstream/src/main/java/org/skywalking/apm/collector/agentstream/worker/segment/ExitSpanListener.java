package org.skywalking.apm.collector.agentstream.worker.segment;

import org.skywalking.apm.network.proto.SpanObject;

/**
 * @author pengys5
 */
public interface ExitSpanListener extends SpanListener {
    void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId);
}
