package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanType;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 *
 * @author wusheng
 */
public interface AbstractTracerContext {
    void inject(ContextCarrier carrier);

    void extract(ContextCarrier carrier);

    String getGlobalTraceId();

    AbstractSpan createSpan(String operationName, SpanType spanType);

    AbstractSpan createSpan(String operationName, SpanType spanType, Injectable injectable);

    AbstractSpan activeSpan();

    void stopSpan(AbstractSpan span);

    void dispose();
}
