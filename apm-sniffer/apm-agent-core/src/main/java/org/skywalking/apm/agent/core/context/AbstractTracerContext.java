package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 *
 * @author wusheng
 */
public interface AbstractTracerContext {
    void inject(ContextCarrier carrier);

    void extract(ContextCarrier carrier);

    ContextSnapshot capture();

    void continued(ContextSnapshot snapshot);

    String getGlobalTraceId();

    AbstractSpan createEntrySpan(String operationName);

    AbstractSpan createLocalSpan(String operationName);

    AbstractSpan createExitSpan(String operationName, String remotePeer);

    AbstractSpan activeSpan();

    void stopSpan(AbstractSpan span);
}
