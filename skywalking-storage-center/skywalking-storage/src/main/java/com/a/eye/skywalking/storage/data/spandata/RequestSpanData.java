package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.RequestSpan;

/**
 * Created by xin on 2016/11/12.
 */
public class RequestSpanData extends AbstractSpanData {
    private RequestSpan requestSpan;

    public RequestSpanData(RequestSpan requestSpan) {
        this.requestSpan = requestSpan;
    }

    public RequestSpanData() {
    }

    @Override
    public SpanType getSpanType() {
        return SpanType.RequestSpan;
    }

    @Override
    public long getTraceStartTime() {
        return buildTraceStartTime(requestSpan.getTraceId());
    }

    @Override
    public byte[] toByteArray() {
        return requestSpan.toByteArray();
    }

    @Override
    public String getTraceId() {
        return requestSpan.getTraceId();
    }

    @Override
    public String getLevelId() {
        return buildLevelId(requestSpan.getParentLevel(), requestSpan.getLevelId());
    }

    public String getAddress(){
        return requestSpan.getAddress();
    }

    public String getApplicationId(){
        return requestSpan.getApplicationId();
    }

    public String getProcessNo(){
        return requestSpan.getProcessNo();
    }

    public long getStartTime(){
        return requestSpan.getStartDate();
    }

    public String getBusinessKey() {
        return requestSpan.getBussinessKey();
    }

    public String getCallType() {
        return requestSpan.getCallType();
    }

    public int getType() {
        return requestSpan.getSpanType();
    }
}
