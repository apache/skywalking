package org.skywalking.apm.agent.core.context.trace;


/**
 * LeafSpan is a special type of {@link Span}
 *
 * In rpc-client tracing scenario, one component could constitute by many other rpc-client.
 * e.g Feign constitutes by okhttp, apache httpclient, etc.
 *
 * By having leaf concept, no need so many spans for single rpc call.
 *
 * @author wusheng
 */
public class LeafSpan extends Span {
    private int stackDepth = 0;

    /**
     * Create a new span, by given span id and give startTime but no parent span id,
     * No parent span id means that, this Span is the first span of the {@link TraceSegment}
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param operationName {@link #operationName}
     * @param startTime given start time of span
     */
    public LeafSpan(int spanId, String operationName, long startTime) {
        super(spanId, -1, operationName, startTime);
    }

    /**
     * Create a new span, by given span id, parent span, operationName and startTime.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpan {@link Span}
     * @param operationName {@link #operationName}
     * @param startTime given start timestamp
     */
    public LeafSpan(int spanId, Span parentSpan, String operationName, long startTime) {
        super(spanId, parentSpan.getSpanId(), operationName, startTime);
    }

    public void push() {
        stackDepth++;
    }

    public void pop() {
        stackDepth--;
    }

    public boolean isFinished() {
        return stackDepth == 0;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    private boolean isInOwnerContext() {
        return stackDepth == 1;
    }

    /**
     * Sets the string name for the logical operation this span represents,
     * only when this is in context of the leaf span owner.
     *
     * @return this Span instance, for chaining
     */
    @Override
    public Span setOperationName(String operationName) {
        if (isInOwnerContext()) {
            super.setOperationName(operationName);
        }
        return this;
    }

    /**
     * Set a key:value tag on the Span,
     * only when this is in context of the leaf span owner.
     *
     * @return this Span instance, for chaining
     */
    @Override
    public final Span setTag(String key, String value) {
        if (isInOwnerContext()) {
            super.setTag(key, value);
        }
        return this;
    }

    @Override
    public final Span setTag(String key, boolean value) {
        if (isInOwnerContext()) {
            super.setTag(key, value);
        }
        return this;
    }

    @Override
    public final Span setTag(String key, Integer value) {
        if (isInOwnerContext()) {
            super.setTag(key, value);
        }
        return this;
    }
}
