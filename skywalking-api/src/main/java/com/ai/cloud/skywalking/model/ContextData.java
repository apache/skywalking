package com.ai.cloud.skywalking.model;

import com.ai.cloud.skywalking.context.Span;

public class ContextData {
    private String traceId;
    private String parentLevel;
    private long levelId;
    private char spanType;

    ContextData() {

    }

    public ContextData(Span span) {
        this.traceId = span.getTraceId();
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
        this.spanType = span.getSpanType();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public long getLevelId() {
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
