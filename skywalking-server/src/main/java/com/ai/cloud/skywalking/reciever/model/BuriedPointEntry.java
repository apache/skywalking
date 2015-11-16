package com.ai.cloud.skywalking.reciever.model;

import java.util.Date;

public class BuriedPointEntry {
    private String traceId;
    private String parentLevel;
    private int levelId;
    private String viewPointId;
    private Date startDate;
    private long cost;
    private String address;
    private byte statusCode = 0;
    private String exceptionStack;
    private char spanType;
    private boolean isReceiver = false;
    private String businessKey;
    private String processNo;


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

    public Date getStartDate() {
        return startDate;
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

    public char getSpanType() {
        return spanType;
    }

    public boolean isReceiver() {
        return isReceiver;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getProcessNo() {
        return processNo;
    }

    public static BuriedPointEntry convert(String str) {
        BuriedPointEntry result = new BuriedPointEntry();
        String[] fieldValues = str.split("-");
        result.traceId = fieldValues[0];
        result.parentLevel = fieldValues[1];
        result.levelId = Integer.valueOf(fieldValues[2]);
        result.viewPointId = fieldValues[3];
        result.startDate = new Date(Long.valueOf(fieldValues[4]));
        result.cost = Long.getLong(fieldValues[5]);
        result.address = fieldValues[6];
        result.exceptionStack = fieldValues[7];
        result.spanType = fieldValues[8].charAt(0);
        result.isReceiver = Boolean.getBoolean(fieldValues[9]);
        result.businessKey = fieldValues[10];
        result.processNo = fieldValues[11];
        return result;
    }
}
