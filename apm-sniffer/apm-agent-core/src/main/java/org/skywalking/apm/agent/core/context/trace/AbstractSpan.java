package org.skywalking.apm.agent.core.context.trace;


/**
 * The <code>AbstractSpan</code> represents the span's skeleton,
 * which contains all open methods.
 *
 * @author wusheng
 */
public interface AbstractSpan {
    void setLayer(SpanLayer layer);

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

    void errorOccurred();

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
