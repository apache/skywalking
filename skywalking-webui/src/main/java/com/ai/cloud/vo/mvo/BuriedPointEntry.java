package com.ai.cloud.vo.mvo;

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


    private BuriedPointEntry() {

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
        result.traceId = fieldValues[0].trim();
        result.parentLevel = fieldValues[1].trim();
        result.levelId = Integer.valueOf(fieldValues[2]);
        result.viewPointId = fieldValues[3].trim();
        result.startDate = new Date(Long.valueOf(fieldValues[4]));
        result.cost = Long.parseLong(fieldValues[5]);
        result.address = fieldValues[6].trim();
        result.exceptionStack = fieldValues[7].trim();
        result.spanType = fieldValues[8].charAt(0);
        result.isReceiver = Boolean.getBoolean(fieldValues[9]);
        result.businessKey = fieldValues[10].replace('-', '^').trim();
        result.processNo = fieldValues[11].trim();
        return result;
    }

	@Override
	public String toString() {
		return "BuriedPointEntry [traceId=" + traceId + ", parentLevel=" + parentLevel + ", levelId=" + levelId
				+ ", viewPointId=" + viewPointId + ", startDate=" + startDate + ", cost=" + cost + ", address="
				+ address + ", statusCode=" + statusCode + ", exceptionStack=" + exceptionStack + ", spanType="
				+ spanType + ", isReceiver=" + isReceiver + ", businessKey=" + businessKey + ", processNo=" + processNo
				+ "]";
	}

}
