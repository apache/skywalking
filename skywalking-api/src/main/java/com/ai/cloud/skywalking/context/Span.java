package com.ai.cloud.skywalking.context;

import com.ai.cloud.skywalking.constants.Constants;
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
        toStringValue.append(traceId + Constants.SPAN_FIELD_SPILT_PATTERN);

        if (!StringUtil.isEmpty(parentLevel)) {
            toStringValue.append(parentLevel + Constants.SPAN_FIELD_SPILT_PATTERN);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        toStringValue.append(levelId + Constants.SPAN_FIELD_SPILT_PATTERN);

        if (!StringUtil.isEmpty(viewPointId)) {
            toStringValue.append(viewPointId + Constants.SPAN_FIELD_SPILT_PATTERN);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        toStringValue.append(startDate + Constants.SPAN_FIELD_SPILT_PATTERN);
        toStringValue.append(cost + Constants.SPAN_FIELD_SPILT_PATTERN);

        if (!StringUtil.isEmpty(address)) {
            toStringValue.append(address + Constants.SPAN_FIELD_SPILT_PATTERN);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        toStringValue.append(statusCode + Constants.SPAN_FIELD_SPILT_PATTERN);

        if (!StringUtil.isEmpty(exceptionStack)) {
            toStringValue.append(exceptionStack + Constants.SPAN_FIELD_SPILT_PATTERN);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        toStringValue.append(spanType + Constants.SPAN_FIELD_SPILT_PATTERN);
        toStringValue.append(isReceiver + Constants.SPAN_FIELD_SPILT_PATTERN);


        if (!StringUtil.isEmpty(businessKey)) {
            toStringValue.append(businessKey.replaceAll(Constants.NEW_LINE_CHARACTER_PATTERN,
                    Constants.BUSINESSKEY_SPILT_PATTERN) + Constants.SPAN_FIELD_SPILT_PATTERN);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        if (!StringUtil.isEmpty(processNo)) {
            toStringValue.append(processNo);
        } else {
            toStringValue.append(" " + Constants.SPAN_FIELD_SPILT_PATTERN);
        }

        return toStringValue.toString();
    }
}
