package org.skywalking.apm.agent.core.context.trace;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.core.context.util.ThrowableTransformer;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;

/**
 * The <code>AbstractTracingSpan</code> represents a group of {@link AbstractSpan} implementations,
 * which belongs a real distributed trace.
 *
 * @author wusheng
 */
public abstract class AbstractTracingSpan implements AbstractSpan {
    protected int spanId;
    protected int parentSpanId;
    protected List<KeyValuePair> tags;
    protected String operationName;
    protected int operationId;
    /**
     * The start time of this Span.
     */
    protected long startTime;
    /**
     * The end time of this Span.
     */
    protected long endTime;
    /**
     * Error has occurred in the scope of span.
     */
    protected boolean errorOccurred = false;

    /**
     * Log is a concept from OpenTracing spec.
     * <p>
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
    protected List<LogDataEntity> logs;

    protected AbstractTracingSpan(int spanId, int parentSpanId, String operationName) {
        this.operationName = operationName;
        this.operationId = DictionaryUtil.nullValue();
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    protected AbstractTracingSpan(int spanId, int parentSpanId, int operationId) {
        this.operationName = null;
        this.operationId = operationId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    @Override
    public AbstractTracingSpan tag(String key, String value) {
        if (tags == null) {
            tags = new LinkedList<KeyValuePair>();
        }
        tags.add(new KeyValuePair(key, value));
        return this;
    }

    /**
     * Finish the active Span.
     * When it is finished, it will be archived by the given {@link TraceSegment}, which owners it.
     *
     * @param owner of the Span.
     */
    public boolean finish(TraceSegment owner) {
        this.endTime = System.currentTimeMillis();
        owner.archive(this);
        return true;
    }

    public AbstractSpan start() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

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

    public void errorOccurred() {
        this.errorOccurred = true;
    }

    public int getSpanId() {
        return spanId;
    }

    public int getOperationId() {
        return operationId;
    }
}
