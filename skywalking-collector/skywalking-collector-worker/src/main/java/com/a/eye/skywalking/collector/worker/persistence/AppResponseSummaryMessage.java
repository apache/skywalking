package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.worker.TimeSliceMessage;

/**
 * @author pengys5
 */
public class AppResponseSummaryMessage extends TimeSliceMessage {
    private final String code;
    private final Boolean isError;

    public AppResponseSummaryMessage(String timeSlice, String code, Boolean isError) {
        super(timeSlice);
        this.code = code;
        this.isError = isError;
    }

    public String getCode() {
        return code;
    }

    public Boolean getError() {
        return isError;
    }
}
