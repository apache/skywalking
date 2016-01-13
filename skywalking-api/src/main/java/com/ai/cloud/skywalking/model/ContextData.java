package com.ai.cloud.skywalking.model;


import com.ai.cloud.skywalking.protocol.Span;

public class ContextData {
    private String traceId;
    private String parentLevel;
    private int levelId;
    private String spanType;

    ContextData() {

    }

    public ContextData(Span span) {
        this.traceId = span.getTraceId();
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
        this.spanType = span.getSpanType();
    }

    public ContextData(String contextDataStr) {
        // 反序列化参数
        String[] value = contextDataStr.split("-");
        if (value == null || value.length != 4) {
            throw new IllegalArgumentException("illegal context data.");
        }
        this.traceId = value[0];
        this.parentLevel = value[1].trim();
        this.levelId = Integer.valueOf(value[2]);
        this.spanType = value[3];

    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public String getSpanType() {
        return spanType;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(traceId);
        stringBuilder.append("-");
        if (parentLevel == null || parentLevel.length() == 0) {
            stringBuilder.append(" ");
        } else {
            stringBuilder.append(parentLevel);
        }
        stringBuilder.append("-");
        stringBuilder.append(levelId);
        stringBuilder.append("-");
        stringBuilder.append(spanType);
        return stringBuilder.toString();
    }
}
