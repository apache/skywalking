package org.skywalking.apm.agent.core.context.trace;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;

/**
 * The <code>AbstractTracingSpan</code> represents a group of {@link AbstractSpan} implementations,
 * which belongs a real distributed trace.
 *
 * @author wusheng
 */
public abstract class AbstractTracingSpan extends AbstractSpan {
    protected int spanId;
    protected int parentSpanId;
    protected List<KeyValuePair> tags;

    protected AbstractTracingSpan(int spanId, int parentSpanId, String operationName) {
        super(operationName);
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

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
}
