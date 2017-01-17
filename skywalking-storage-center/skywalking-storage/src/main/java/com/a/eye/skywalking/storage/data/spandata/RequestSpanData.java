package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.model.Tag;

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
    public String getTraceLevelId() {
        return buildLevelId(requestSpan.getParentLevel(), requestSpan.getLevelId());
    }

    public String getParentLevelId() {
        return requestSpan.getParentLevel();
    }

    public int getLevelId() {
        return requestSpan.getLevelId();
    }


    public String getAddress() {
        return requestSpan.getTagsMap().get(Tag.ADDRESS.key());
    }

    public String getApplicationCode() {
        return requestSpan.getTagsMap().get(Tag.APPLICATION_CODE.key());
    }

    public int getProcessNo() {
        return Integer.parseInt((requestSpan.getTagsMap().get(Tag.PROCESS_NO.key())));
    }

    public long getStartTime() {
        return requestSpan.getStartTimestamp();
    }

    public String getBusinessKey() {
        String businessKey = requestSpan.getTagsMap().get(Tag.BUSINESS_KEY.key());
        if (businessKey == null) {
            return "";
        }

        return businessKey;
    }

    public String getCallType() {
        return requestSpan.getTagsMap().get(Tag.CALL_TYPE.key());
    }

    public int getType() {
        return Integer.parseInt(requestSpan.getTagsMap().get(Tag.SPAN_TYPE.key()));
    }

    public String getViewPoint() {
        return requestSpan.getTagsMap().get(Tag.VIEW_POINT.key());
    }

    public String getSpanTypeDesc() {
        return requestSpan.getTagsMap().get(Tag.CALL_DESC.key());
    }
}
