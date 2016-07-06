package com.ai.cloud.skywalking.model;


import com.ai.cloud.skywalking.protocol.Span;

public class ContextData {
    private String traceId;
    private String parentLevel;
    private int levelId;

    ContextData() {

    }

    public ContextData(String traceId, String parentLevel) {
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
        if (value == null || value.length != 4) {
            throw new IllegalArgumentException("illegal context data.");
        }
        this.traceId = value[0];
        this.parentLevel = value[1].trim();
        this.levelId = Integer.valueOf(value[2]);

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
        return stringBuilder.toString();
    }
}
