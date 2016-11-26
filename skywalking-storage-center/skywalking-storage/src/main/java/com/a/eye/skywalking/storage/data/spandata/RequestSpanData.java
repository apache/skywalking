package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;

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

    public void setRequestSpan(RequestSpan requestSpan) {
        this.requestSpan = requestSpan;
    }

    @Override
    public SpanType getSpanType() {
        return SpanType.RequestSpan;
    }

    @Override
    public long getTraceStartTime() {
        return requestSpan.getTraceId().getSegments(1);
    }

    @Override
    public byte[] toByteArray() {
        return requestSpan.toByteArray();
    }

    @Override
    public Long[] getTraceIdSegments() {
        return traceIdToArrays(requestSpan.getTraceId());
    }

    @Override
    public String getLevelId() {
        return buildLevelId(requestSpan.getParentLevel(), requestSpan.getLevelId());
    }

    public String getAddress() {
        return requestSpan.getAddress();
    }

    public String getApplicationId() {
        return requestSpan.getApplicationId();
    }

    public int getProcessNo() {
        return requestSpan.getProcessNo();
    }

    public long getStartTime() {
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
