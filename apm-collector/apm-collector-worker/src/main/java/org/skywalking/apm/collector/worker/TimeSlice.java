package org.skywalking.apm.collector.worker;

/**
 * @author pengys5
 */
public abstract class TimeSlice {
    private String sliceType;
    private long startTime;
    private long endTime;

    public TimeSlice(String sliceType, long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.sliceType = sliceType;
    }

    public String getSliceType() {
        return sliceType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }
}
