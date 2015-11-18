package com.ai.cloud.skywalking.reciever.model;

import com.ai.cloud.skywalking.reciever.constants.Constants;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    //  元数据
    private String originData;

    private BuriedPointEntry() {

    }

    private BuriedPointEntry(String originData) {

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

    public String getOriginData() {
        return originData;
    }

    public static BuriedPointEntry convert(String str) {
        BuriedPointEntry result = new BuriedPointEntry();
        String[] fieldValues = str.split(Constants.spiltRegx);
        result.traceId = fieldValues[0].trim();
        result.parentLevel = fieldValues[1].trim();
        result.levelId = Integer.valueOf(fieldValues[2]);
        result.viewPointId = fieldValues[3].trim();
        result.startDate = new Date(Long.valueOf(fieldValues[4]));
        result.cost = Long.parseLong(fieldValues[5]);
        result.address = fieldValues[6].trim();
        result.statusCode = Byte.valueOf(fieldValues[7].trim());
        result.exceptionStack = fieldValues[8].trim();
        result.spanType = fieldValues[9].charAt(0);
        result.isReceiver = Boolean.getBoolean(fieldValues[10]);
        result.businessKey = fieldValues[11].trim();
        result.processNo = fieldValues[12].trim();
        result.originData = str;
        return result;
    }

    private static String[] spilt(String spilt, String value) {
        List<String> list = new ArrayList<String>();
        int resultSize = 0;
        int start = 0, offset = 0;
        for (int index = 0; index < value.length() - 1; index++, offset++) {
            if (spilt.charAt(0) == value.charAt(index) && spilt.charAt(1) == value.charAt(index + 1)) {
                list.add(value.substring(start, offset));
                index++;offset++;
                start = offset + 1;
                resultSize++;
            }
        }
        if (start < value.length()) {
            list.add(value.substring(start, offset));
            resultSize++;
        }
        String[] result = new String[resultSize];
        return list.subList(0, resultSize).toArray(result);
    }

    @Override
    public String toString() {
        return "BuriedPointEntry{" +
                "traceId='" + traceId + '\'' +
                ", parentLevel='" + parentLevel + '\'' +
                ", levelId=" + levelId +
                ", viewPointId='" + viewPointId + '\'' +
                ", startDate=" + startDate +
                ", cost=" + cost +
                ", address='" + address + '\'' +
                ", statusCode=" + statusCode +
                ", exceptionStack='" + exceptionStack + '\'' +
                ", spanType=" + spanType +
                ", isReceiver=" + isReceiver +
                ", businessKey='" + businessKey + '\'' +
                ", processNo='" + processNo + '\'' +
                '}';
    }
}
