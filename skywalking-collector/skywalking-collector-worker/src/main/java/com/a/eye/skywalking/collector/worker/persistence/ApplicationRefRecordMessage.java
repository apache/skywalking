package com.a.eye.skywalking.collector.worker.persistence;

/**
 * @author pengys5
 */
public class ApplicationRefRecordMessage {
    private final String code;
    private final String refCode;

    public ApplicationRefRecordMessage(String code, String refCode) {
        this.code = code;
        this.refCode = refCode;
    }

    public String getCode() {
        return code;
    }

    public String getRefCode() {
        return refCode;
    }
}
