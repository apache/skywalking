package com.a.eye.skywalking.collector.worker;

/**
 * @author pengys5
 */
public abstract class TimeSliceMessage {
    private final String timeSlice;

    public TimeSliceMessage(String timeSlice) {
        this.timeSlice = timeSlice;
    }

    public String getTimeSlice() {
        return timeSlice;
    }
}
