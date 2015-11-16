package com.ai.cloud.skywalking.context;

import com.ai.cloud.skywalking.util.StringUtil;

public class Span {
    private String traceId;
    private String parentLevel;
    private int levelId;
    private String viewPointId;
    private long startDate;
    private long cost;
    private String address;
    private byte statusCode = 0;
    private String exceptionStack;
    private char spanType;
    private boolean isReceiver = false;
    private String businessKey;
    private String processNo;

    public Span(String traceId) {
        this.traceId = traceId;
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
        if (!StringUtil.isEmpty(parentLevel)) {
            return parentLevel;
        }
        return "";
    }

    public void setParentLevel(String parentLevel) {
        this.parentLevel = parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public char getSpanType() {
        return spanType;
    }

    public void setSpanType(char spanType) {
        this.spanType = spanType;
    }

    public boolean isReceiver() {
        return isReceiver;
    }

    public void setReceiver(boolean receiver) {
        isReceiver = receiver;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    @Override
    public String toString() {
        StringBuilder toStringValue = new StringBuilder();
        toStringValue.append(traceId + "-");

        if (!StringUtil.isEmpty(parentLevel)) {
            toStringValue.append(parentLevel + "-");
        } else {
            toStringValue.append(" -");
        }

        toStringValue.append(levelId + "-");

        if (!StringUtil.isEmpty(viewPointId)) {
            toStringValue.append(viewPointId + "-");
        } else {
            toStringValue.append(" -");
        }

        toStringValue.append(startDate + "-");
        toStringValue.append(cost + "-");

        if (!StringUtil.isEmpty(address)) {
            toStringValue.append(address + "-");
        } else {
            toStringValue.append(" -");
        }

        toStringValue.append(statusCode + "-");

        if (!StringUtil.isEmpty(exceptionStack)) {
            toStringValue.append(exceptionStack + "-");
        } else {
            toStringValue.append(" -");
        }

        toStringValue.append(spanType + "-");
        toStringValue.append(isReceiver + "-");


        if (!StringUtil.isEmpty(businessKey)) {
            toStringValue.append(businessKey + "-");
        } else {
            toStringValue.append(" -");
        }

        if (!StringUtil.isEmpty(processNo)) {
            toStringValue.append(processNo);
        } else {
            toStringValue.append(" -");
        }

        return toStringValue.toString();
    }
}
