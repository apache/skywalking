package com.a.eye.skywalking.storage.data.spandata;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

import java.util.*;

/**
 * Created by xin on 2016/11/12.
 */
public class SpanDataHelper {
    public HashMap<String, RequestSpanData> levelIdRequestSpanDataMapping = new HashMap<String, RequestSpanData>();
    public HashMap<String, AckSpanData> levelIdAckSpanDataMapping = new HashMap<String, AckSpanData>();

    private List<SpanData> data;

    public SpanDataHelper(List<SpanData> data) {
        this.data = data;
    }

    public SpanDataHelper category() {
        for (SpanData spanData : data) {
            if (spanData instanceof RequestSpanData) {
                levelIdRequestSpanDataMapping.put(spanData.getTraceLevelId(), (RequestSpanData) spanData);
            } else {
                levelIdAckSpanDataMapping.put(spanData.getTraceLevelId(), (AckSpanData) spanData);
            }
        }

        return this;
    }

    public List<Span> mergeData() {
        List<Span> span = new ArrayList<Span>();
        for (Map.Entry<String, RequestSpanData> entry : levelIdRequestSpanDataMapping.entrySet()) {
            AckSpanData ackSpanData = levelIdAckSpanDataMapping.get(entry.getKey());
            if (ackSpanData != null) {
                span.add(mergeSpan(entry.getValue(), ackSpanData));
            }
        }

        return span;
    }

    private Span mergeSpan(RequestSpanData requestSpanData, AckSpanData ackSpanData) {
        Span.Builder builder = Span.newBuilder().setAddress(requestSpanData.getAddress())
                .setApplicationCode(requestSpanData.getApplicationCode()).setBusinessKey(requestSpanData.getBusinessKey())
                .setCallType(requestSpanData.getCallType()).setCost(ackSpanData.getCost());
        if (ackSpanData.getExceptionStack() != null && ackSpanData.getExceptionStack().length() > 0) {
            builder = builder.setExceptionStack(ackSpanData.getExceptionStack());
        }

        builder = builder.setLevelId(requestSpanData.getLevelId()).setParentLevelId(requestSpanData.getParentLevelId()).setProcessNo(requestSpanData.getProcessNo())
                .setSpanType(requestSpanData.getType()).setStartTime(requestSpanData.getStartTime())
                .setStatusCode(ackSpanData.getStatusCode())
                .setViewpoint(requestSpanData.getViewPoint())
                .setTraceId(TraceId.newBuilder().addAllSegments(Arrays.asList(requestSpanData.getTraceIdSegments())));
        return builder.build();
    }

}
