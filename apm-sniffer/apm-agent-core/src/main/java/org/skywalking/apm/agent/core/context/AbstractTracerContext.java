package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.skywalking.apm.agent.core.context.trace.LocalSpan;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 *
 * @author wusheng
 */
public interface AbstractTracerContext {
    /**
     * Prepare for the cross-process propagation.
     * How to initialize the carrier, depends on the implementation.
     *
     * @param carrier to carry the context for crossing process.
     * @see {@link TracingContext} and {@link IgnoredTracerContext}
     */
    void inject(ContextCarrier carrier);

    /**
     * Build the reference between this segment and a cross-process segment.
     * How to build, depends on the implementation.
     *
     * @param carrier carried the context from a cross-process segment.
     * @see {@link TracingContext} and {@link IgnoredTracerContext}
     */
    void extract(ContextCarrier carrier);

    /**
     * Capture a snapshot for cross-thread propagation.
     * It's a similar concept with ActiveSpan.Continuation in OpenTracing-java
     * How to build, depends on the implementation.
     *
     * @return the {@link ContextSnapshot} , which includes the reference context.
     * @see {@link TracingContext} and {@link IgnoredTracerContext}
     */
    ContextSnapshot capture();

    /**
     * Build the reference between this segment and a cross-thread segment.
     * How to build, depends on the implementation.
     *
     * @param snapshot from {@link #capture()} in the parent thread.
     * @see {@link TracingContext} and {@link IgnoredTracerContext}
     */
    void continued(ContextSnapshot snapshot);

    /**
     * Get the global trace id, if exist.
     * How to build, depends on the implementation.
     *
     * @return the string represents the id.
     * @see {@link TracingContext} and {@link IgnoredTracerContext}
     */
    String getGlobalTraceId();

    /**
     * Create an entry span
     *
     * @param operationName most likely a service name
     * @return the span represents an entry point of this segment.
     * @see {@link EntrySpan} if the implementation is {@link TracingContext}
     */
    AbstractSpan createEntrySpan(String operationName);

    /**
     * Create a local span
     *
     * @param operationName most likely a local method signature, or business name.
     * @return the span represents a local logic block.
     * @see {@link LocalSpan} if the implementation is {@link TracingContext}
     */
    AbstractSpan createLocalSpan(String operationName);

    /**
     * Create an exit span
     *
     * @param operationName most likely a service name of remote
     * @param remotePeer the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.)
     * @return the span represent an exit point of this segment.
     * @see {@link ExitSpan} if the implementation is {@link TracingContext}
     */
    AbstractSpan createExitSpan(String operationName, String remotePeer);

    /**
     * @return the active span of current tracing context(stack)
     */
    AbstractSpan activeSpan();

    /**
     * Finish the given span, and the given span should be the active span of current tracing context(stack)
     *
     * @param span to finish
     */
    void stopSpan(AbstractSpan span);
}
