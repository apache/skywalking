package com.ai.cloud.skywalking.protocol;

public class FullSpan {

    private String traceId;
    private String parentLevel = "";
    private int    levelId     = 0;
    private String viewPointId;
    private String applicationId;
    private String callType;
    private long   cost;
    private String businessKey;
    private String exceptionStack;
    private byte statusCode = 0;
    private String spanTypeDesc;
    private String userId;
    private long   startDate;
    private String spanType;

    public FullSpan() {

    }

    public FullSpan(RequestSpan span) {
        this.traceId = span.getTraceId();
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
        this.applicationId = span.getApplicationId();
        this.callType = span.getCallType();
        this.businessKey = span.getBusinessKey();
        this.spanTypeDesc = span.getSpanTypeDesc();
        this.userId = span.getUserId();
        this.startDate = span.getStartDate();
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

    public String getViewPointId() {
        return viewPointId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCallType() {
        return callType;
    }

    public long getCost() {
        return cost;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public String getSpanTypeDesc() {
        return spanTypeDesc;
    }

    public void setSpanTypeDesc(String spanTypeDesc) {
        this.spanTypeDesc = spanTypeDesc;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getStartDate() {
        return startDate;
    }

    public String getSpanType() {
        return spanType;
    }

    public void setSpanType(String spanType) {
        this.spanType = spanType;
    }

    public String getTraceLevelId() {
        if (getParentLevel() != null && getParentLevel().length() > 0) {
            return getParentLevel() + "." + getLevelId();
        }
        return getLevelId() + "";
    }

    public void addAckSpan(AckSpan ackSpan) {
        if (ackSpan != null) {
            this.cost = ackSpan.getCost();
            this.exceptionStack = ackSpan.getExceptionStack();
            this.statusCode = ackSpan.getStatusCode();
        }
    }

    public void setParentLevel(String parentLevel) {
        this.parentLevel = parentLevel;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }
}
