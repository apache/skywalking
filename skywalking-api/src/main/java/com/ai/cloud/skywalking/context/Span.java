package com.ai.cloud.skywalking.context;

import com.ai.cloud.skywalking.util.StringUtil;

public class Span {
    private String traceId;
    private String parentLevel;
    private long levelId;
    private String viewPointId;
    private long startDate;
    private long cost;
    private String address;
    private byte statueCode = 0;
    private String exceptionStack;
    private byte spanType;

    public Span() {
    }

    public Span(String traceId) {
        this.traceId = traceId;
    }

    public Span(String traceId, String parentLevel) {
        this.traceId = traceId;
        this.parentLevel = parentLevel;
    }

    public String getProcessNo() {
        return processNo;
    }

    public void setProcessNo(String processNo) {
        this.processNo = processNo;
    }


    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public String getViewPointId() {
        return viewPointId;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public void setParentLevel(String parentLevel) {
        this.parentLevel = parentLevel;
    }

    public long getLevelId() {
        return levelId;
    }

    public void setLevelId(long levelId) {
        this.levelId = levelId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    private String processNo;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public byte getStatueCode() {
        return statueCode;
    }

    public void setStatueCode(byte statueCode) {
        this.statueCode = statueCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public byte getSpanType() {
        return spanType;
    }

    public void setSpanType(byte spanType) {
        this.spanType = spanType;
    }

    @Override
    public String toString() {
        StringBuffer stringBuffer = new StringBuffer(traceId + "-" + parentLevel + "-"
                + levelId + "-" + viewPointId + "-" + startDate + "-" + cost +
                "-" + address +
                "-" + statueCode +
                "-" + processNo + "-" + spanType);
        if (!StringUtil.isEmpty(exceptionStack)) {
            stringBuffer.append("-" + exceptionStack);
        }

        return stringBuffer.toString();
    }
}
