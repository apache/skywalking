package com.ai.cloud.skywalking.protocol;

public abstract class SpanData {

    protected static final String SPAN_FIELD_SPILT_PATTERN = "@~";
    protected static final String SPAN_ATTR_SPILT_CHARACTER = "#~";
    protected static final String NEW_LINE_CHARACTER_PATTERN = "\n";
    protected static final String CARRIAGE_RETURN_CHARACTER_PATTERN = "\r";

    protected String traceId;
    protected String parentLevel;
    protected int levelId = 0;
    protected String viewPointId = "";
    protected long startDate = System.currentTimeMillis();
    protected long cost = 0L;
    protected String address = "";
    protected byte statusCode = 0;
    protected String exceptionStack;
    protected char spanType = 'M';
    protected boolean isReceiver = false;
    protected String businessKey = "";
    protected String processNo = "";
    protected String applicationId = "";
    protected String originData = "";


    public String getTraceId() {
        return traceId;
    }

    public String getParentLevel() {
        return parentLevel;
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

    public String getViewPointId() {
        return viewPointId;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public void setProcessNo(String processNo) {
        this.processNo = processNo;
    }

    public String getOriginData() {
        return originData;
    }

    public long getCost() {
        return cost;
    }

    public String getAddress() {
        return address;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getProcessNo() {
        return processNo;
    }

    public String getApplicationId() {
        return applicationId;
    }

}
