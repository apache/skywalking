package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.network.trace.component.Component;

/**
 * The <code>AbstractSpan</code> represents the span's skeleton,
 * which contains all open methods.
 *
 * @author wusheng
 */
public interface AbstractSpan {
    /**
     * Set the component id, which defines in {@link org.skywalking.apm.network.trace.component.ComponentsDefine}
     * @param component
     */
    AbstractSpan setComponent(Component component);

    AbstractSpan setComponent(String componentName);

    AbstractSpan setLayer(SpanLayer layer);

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan tag(String key, String value);

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    AbstractSpan log(Throwable t);

    AbstractSpan errorOccurred();

    /**
     * @return true if the actual span is an entry span.
     */
    boolean isEntry();

    /**
     * @return true if the actual span is a local span.
     */
    boolean isLocal();

    /**
     * @return true if the actual span is an exit span.
     */
    boolean isExit();
}
