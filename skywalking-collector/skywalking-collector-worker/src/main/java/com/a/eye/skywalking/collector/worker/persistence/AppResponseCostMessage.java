package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.worker.TimeSliceMessage;

/**
 * @author pengys5
 */
public class AppResponseCostMessage extends TimeSliceMessage {
    private final String code;
    private final Boolean isError;
    private final Long startTime;
    private final Long endTime;

    public AppResponseCostMessage(String timeSlice, String code, Boolean isError, Long startTime, Long endTime) {
        super(timeSlice);
        this.code = code;
        this.isError = isError;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getCode() {
        return code;
    }

    public Boolean getError() {
        return isError;
    }

    public Long getStartTime() {
        return startTime;
    }

    public Long getEndTime() {
        return endTime;
    }
}
