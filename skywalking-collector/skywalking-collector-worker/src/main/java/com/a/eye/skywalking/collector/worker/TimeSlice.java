package com.a.eye.skywalking.collector.worker;

/**
 * @author pengys5
 */
public abstract class TimeSlice {
    private long timeSlice;
    private String sliceType;

    public TimeSlice(String sliceType, long timeSlice) {
        this.timeSlice = timeSlice;
        this.sliceType = sliceType;
    }

    public long getTimeSlice() {
        return timeSlice;
    }

    public String getSliceType() {
        return sliceType;
    }
}
