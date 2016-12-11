package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.network.grpc.Span;

/**
 * Created by wusheng on 2016/12/5.
 */
public class FullSpan {


    private String parentLevelId;
    protected String traceId;
    protected int levelId;
    protected String viewPointId;
    protected String applicationId;
    protected String callType;
    protected long cost;
    protected String businessKey;
    protected String exceptionStack;
    protected byte statusCode = 0;
    protected String spanTypeDesc;
    protected String username;
    protected long startDate;
    protected String spanType;
    protected String address = "";
    protected String processNo = "";

    public FullSpan() {

    }

    public FullSpan(Span span) {
        StringBuilder traceId = new StringBuilder();
        for (Long segment : span.getTraceId().getSegmentsList()) {
            traceId.append(segment).append(".");
        }
        this.traceId = traceId.substring(0, traceId.length() - 1);
        this.levelId = span.getLevelId();
        this.parentLevelId = span.getParentLevelId();
        this.applicationId = span.getApplicationCode();
        this.callType = span.getCallType();
        this.businessKey = span.getBusinessKey();
        this.spanTypeDesc = span.getSpanTypeDesc();
        this.username = span.getUsername();
        this.startDate = span.getStartTime();
        this.viewPointId = span.getViewpoint();
        this.spanType = span.getSpanType() + "";
        this.address = span.getAddress();
        this.processNo = span.getProcessNo() + "";

        this.cost = span.getCost();
        this.exceptionStack = span.getExceptionStack();
        this.statusCode = (byte) span.getStatusCode();
    }

    public String getTraceId() {
        return traceId;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getStartDate() {
        return startDate;
    }

    public String getSpanType() {
        return spanType;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public void setSpanType(String spanType) {
        this.spanType = spanType;
    }

    public String getTraceLevelId() {
        return parentLevelId == null || parentLevelId.length() == 0? levelId + "" : parentLevelId + "." + levelId;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }

    public String getAddress() {
        return address;
    }

    public String getProcessNo() {
        return processNo;
    }

    public String getParentLevelId() {
        return parentLevelId;
    }
}
