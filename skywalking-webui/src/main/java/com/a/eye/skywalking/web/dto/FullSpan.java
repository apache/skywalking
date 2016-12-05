package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.network.grpc.Span;

/**
 * Created by wusheng on 2016/12/5.
 */
public class FullSpan {


    protected String traceId;
    protected String levelId = "";
    protected String viewPointId;
    protected String applicationId;
    protected String callType;
    protected long   cost;
    protected String businessKey;
    protected String exceptionStack;
    protected byte statusCode = 0;
    protected String spanTypeDesc;
    protected String userId;
    protected long   startDate;
    protected String spanType;
    protected String address   = "";
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
        this.applicationId = span.getApplicationId();
        this.callType = span.getCallType();
        this.businessKey = span.getBusinessKey();
        this.spanTypeDesc = span.getSpanTypeDesc();
        this.userId = span.getUserId();
        this.startDate = span.getStarttime();
        this.viewPointId = span.getViewpoint();
        this.spanType = span.getSpanType() + "";
        this.address = span.getAddress();
        this.processNo = span.getProcessNo() + "";

        this.cost = span.getCost();
        this.exceptionStack = span.getExceptionStack();
        this.statusCode = (byte)span.getStatusCode();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getLevelId() {
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

    public void setLevelId(String levelId) {
        this.levelId = levelId;
    }

    public void setSpanType(String spanType) {
        this.spanType = spanType;
    }

    public String getTraceLevelId() {
        return getLevelId();
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
}
