package com.a.eye.skywalking.model;


import com.a.eye.skywalking.network.grpc.TraceId;

public class ContextData {
    private TraceId traceId;
    private String parentLevel;
    private int levelId;

    ContextData() {

    }

    public ContextData(TraceId traceId, String parentLevel) {
        this.traceId = traceId;
        this.parentLevel = parentLevel;
    }

    public ContextData(Span span) {
        this.traceId = span.getTraceId();
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
    }

    public ContextData(String contextDataStr) {
        // 反序列化参数
        String[] value = contextDataStr.split("-");
        if (value == null || value.length != 3) {
            throw new IllegalArgumentException("illegal context");
        }
        String traceIdStr = value[0];
        String[] traceIdSegments = traceIdStr.split(",");
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

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder traceIdBuilder = new StringBuilder();
        for (Long segment : traceId.getSegmentsList()) {
            traceIdBuilder.append(segment).append(".");
        }
        stringBuilder.append(traceIdBuilder.substring(0, traceIdBuilder.length() - 1));
        stringBuilder.append("-");
        if (parentLevel == null || parentLevel.length() == 0) {
            stringBuilder.append(" ");
        } else {
            stringBuilder.append(parentLevel);
        }
        stringBuilder.append("-");
        stringBuilder.append(levelId);
        return stringBuilder.toString();
    }
}
