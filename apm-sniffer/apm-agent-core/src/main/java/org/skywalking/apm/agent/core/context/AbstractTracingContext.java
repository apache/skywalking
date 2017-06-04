package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.trace.Span;

/**
 * The <code>AbstractTracingContext</code> provides the major methods of all context implementations.
 * 
 * @author wusheng
 */
public interface AbstractTracingContext {
    Span createSpan(String operationName, boolean isLeaf);

    Span createSpan(String operationName, long startTime, boolean isLeaf);

    Span activeSpan();

    void stopSpan(Span span, Long endTime);

    void stopSpan(Span span);

    void inject(ContextCarrier carrier);

    AbstractTracingContext extract(ContextCarrier carrier);

    String getGlobalTraceId();
}
