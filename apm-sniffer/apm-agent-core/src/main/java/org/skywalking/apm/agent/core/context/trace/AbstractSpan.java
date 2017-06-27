package org.skywalking.apm.agent.core.context.trace;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.core.context.util.ThrowableTransformer;

/**
 * The <code>AbstractSpan</code> represents the span's skeleton,
 * which contains all open methods.
 *
 * @author wusheng
 */
public abstract class AbstractSpan {
    protected String operationName;
    /**
     * The start time of this Span.
     */
    protected long startTime;
    /**
     * The end time of this Span.
     */
    protected long endTime;

    /**
     * Log is a concept from OpenTracing spec.
     * <p>
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
    protected List<LogDataEntity> logs;

    protected AbstractSpan(String operationName) {
        this.operationName = operationName;
    }

    public AbstractSpan start() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    public abstract AbstractSpan tag(String key, String value);

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    public AbstractSpan log(Throwable t) {
        if (logs == null) {
            logs = new LinkedList<LogDataEntity>();
        }
        logs.add(new LogDataEntity.Builder()
            .add(new KeyValuePair("event", "error"))
            .add(new KeyValuePair("error.kind", t.getClass().getName()))
            .add(new KeyValuePair("message", t.getMessage()))
            .add(new KeyValuePair("stack", ThrowableTransformer.INSTANCE.convert2String(t, 4000)))
            .build());
        return this;
    }

    /**
     * @return true if the actual span is an entry span.
     */
    public abstract boolean isEntry();

    /**
     * @return true if the actual span is a local span.
     */
    public abstract boolean isLocal();

    /**
     * @return true if the actual span is an exit span.
     */
    public abstract boolean isExit();
}
