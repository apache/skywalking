package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.AckSpan;

/**
 * Created by xin on 2016/11/12.
 */
public class AckSpanData extends AbstractSpanData {
    private AckSpan ackSpan;

    public AckSpanData(AckSpan ackSpan) {
        this.ackSpan = ackSpan;
    }

    public AckSpanData() {
    }

    @Override
    public SpanType getSpanType() {
        return SpanType.ACKSpan;
    }

    @Override
    public long getTraceStartTime() {
        return buildTraceStartTime(ackSpan.getTraceId());
    }

    @Override
    public byte[] toByteArray() {
        return ackSpan.toByteArray();
    }

    @Override
    public String getTraceId() {
        return ackSpan.getTraceId();
    }

    @Override
    public String getLevelId() {
        return buildLevelId(ackSpan.getParentLevel(), ackSpan.getLevelId());
    }

}
