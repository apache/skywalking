package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.messages.ISerializable;
import com.a.eye.skywalking.trace.messages.proto.SegmentRefMessage;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment},
 * use {@link #spanId} point to the exact span of the ref {@link TraceSegment}.
 *
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentRef implements ISerializable<SegmentRefMessage> {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    private String traceSegmentId;

    /**
     * {@link Span#spanId}
     */
    private int spanId = -1;

    /**
     * Create a {@link TraceSegmentRef} instance, without any data.
     */
    public TraceSegmentRef() {
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public void setTraceSegmentId(String traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    @Override
    public String toString() {
        return "TraceSegmentRef{" +
            "traceSegmentId='" + traceSegmentId + '\'' +
            ", spanId=" + spanId +
            '}';
    }

    @Override
    public SegmentRefMessage serialize() {
        SegmentRefMessage.Builder builder = SegmentRefMessage.newBuilder();
        builder.setTraceSegmentId(traceSegmentId);
        builder.setSpanId(spanId);
        return builder.build();
    }

    @Override
    public void deserialize(SegmentRefMessage message) {
        traceSegmentId = message.getTraceSegmentId();
        spanId = message.getSpanId();
    }
}
