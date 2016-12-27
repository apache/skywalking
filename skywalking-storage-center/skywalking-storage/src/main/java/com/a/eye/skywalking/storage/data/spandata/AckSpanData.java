package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.model.Tag;

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

    public void setAckSpan(AckSpan ackSpan) {
        this.ackSpan = ackSpan;
    }

    @Override
    public SpanType getSpanType() {
        return SpanType.ACKSpan;
    }

    @Override
    public long getTraceStartTime() {
        return ackSpan.getTraceId().getSegments(1);
    }

    @Override
    public byte[] toByteArray() {
        return ackSpan.toByteArray();
    }

    @Override
    public Long[] getTraceIdSegments() {
        return traceIdToArrays(ackSpan.getTraceId());
    }

    @Override
    public String getTraceLevelId() {
        return buildLevelId(ackSpan.getParentLevel(), ackSpan.getLevelId());
    }

    public long getCost() {
        return ackSpan.getCost();
    }

    public String getExceptionStack() {
        String exceptionStack =  ackSpan.getTagsMap().get(Tag.EXCEPTION_STACK.key());
        if (exceptionStack == null){
            return "";
        }

        return exceptionStack;
    }

    public int getStatusCode() {
        return Integer.parseInt(ackSpan.getTagsMap().get(Tag.STATUS.key()));
    }

}
