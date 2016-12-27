package com.a.eye.skywalking.network.model;

/**
 * Created by xin on 2016/12/22.
 */
public enum Tag {
    VIEW_POINT("viewpoint", 1), BUSINESS_KEY("business_key", 2), CALL_TYPE("call.type", 3),
    SPAN_TYPE("type", 4), CALL_DESC("call.desc", 5), USER_NAME("username", 6),
    ADDRESS("hostname", 7), PROCESS_NO("process_no", 8), APPLICATION_CODE("application_code", 9),
    STATUS("error.status", 10), EXCEPTION_STACK("error.exception_stack", 11);

    private String keyName;
    private int keyValue;

    Tag(String keyName, int keyValue) {
        this.keyName = keyName;
        this.keyValue = keyValue;
    }

    public static Tag convert(String value) {
        String[] valueSegment = value.split("_");
        if (valueSegment.length != 2) {
            throw new IllegalArgumentException("Failed to convert to tag[ " + value + "]");
        }

        switch (Integer.parseInt(valueSegment[1])) {
            case 1:
                return VIEW_POINT;
            case 2:
                return BUSINESS_KEY;
            default:
                throw new IllegalArgumentException("Cannot find the tag by keyValue[" + valueSegment[1] + "]");
        }
    }

    public String toString() {
        return keyName + "_" + keyValue;
    }

    public String key() {
        return toString();
    }
}
