package com.a.eye.skywalking.model;


import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.network.grpc.TraceId;

import static com.a.eye.skywalking.conf.Constants.CONTEXT_DATA_SEGMENT_SPILT_CHAR;
import static com.a.eye.skywalking.util.TraceIdUtil.formatTraceId;

public class ContextData {
    private TraceId traceId;
    private String parentLevel;
    private int levelId;
    private int routeKey;

    ContextData() {

    }

    public ContextData(TraceId traceId, String parentLevelId, int routeKey) {
        this.traceId = traceId;
        this.parentLevel = parentLevelId;
        this.routeKey = routeKey;
    }

    public ContextData(Span span) {
        this.traceId = span.getTraceId();
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
        this.routeKey = span.getRouteKey();
    }

    public ContextData(String contextDataStr) {
        // 反序列化参数
        String[] value = contextDataStr.split(CONTEXT_DATA_SEGMENT_SPILT_CHAR);
        if (value == null || value.length != 4) {
            throw new IllegalArgumentException("illegal context");
        }
        String traceIdStr = value[0];
        String[] traceIdSegments = traceIdStr.split("\\.");
        if(traceIdSegments == null || traceIdSegments.length != 6){
            throw new IllegalArgumentException("illegal traceid in context");
        }
        TraceId.Builder traceIdBuilder = TraceId.newBuilder();
        int i = 0;
        for (String traceIdSegment : traceIdSegments) {
            try {
                traceIdBuilder.addSegments(Long.parseLong(traceIdSegment));
            }catch(NumberFormatException e){
                throw new IllegalArgumentException("illegal traceid seg[" + i + "] in context", e);
            }
            i++;
        }
        this.traceId = traceIdBuilder.build();
        this.parentLevel = value[1].trim();
        this.levelId = Integer.valueOf(value[2]);
        this.routeKey = Integer.parseInt(value[3]);
    }

    public TraceId getTraceId() {
        return traceId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public int getRouteKey(){
        return this.routeKey;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(formatTraceId(traceId));
        stringBuilder.append(CONTEXT_DATA_SEGMENT_SPILT_CHAR);
        if (parentLevel == null || parentLevel.length() == 0) {
            stringBuilder.append(" ");
        } else {
            stringBuilder.append(parentLevel);
        }
        stringBuilder.append(CONTEXT_DATA_SEGMENT_SPILT_CHAR);
        stringBuilder.append(levelId);
        stringBuilder.append(CONTEXT_DATA_SEGMENT_SPILT_CHAR);
        stringBuilder.append(routeKey);
        return stringBuilder.toString();
    }
}
