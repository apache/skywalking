package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.Span;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 *
 * @author wusheng
 */
public interface AbstractTracerContext {
    void inject(ContextCarrier carrier);

    void extract(ContextCarrier carrier);

    String getGlobalTraceId();

    Span createSpan(String operationName, boolean isLeaf);

    Span createSpan(String operationName, long startTime, boolean isLeaf);

    Span activeSpan();

    void stopSpan(Span span);

    void stopSpan(Span span, Long endTime);

    void dispose();
}
