package com.ai.cloud.skywalking.model;


import com.ai.cloud.skywalking.protocol.Span;

public class ContextData {
    private String traceId;
    private String parentLevel;
    private int levelId;
    private char spanType;

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
        this.traceId = value[0];
        this.parentLevel = value[1];
        this.levelId = Integer.valueOf(value[2]);
        this.spanType = value[3].charAt(0);

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

    public char getSpanType() {
        return spanType;
    }

    @Override
    public String toString() {
        return traceId + "-" + parentLevel + "-" + levelId + "-" + spanType;
    }
}
