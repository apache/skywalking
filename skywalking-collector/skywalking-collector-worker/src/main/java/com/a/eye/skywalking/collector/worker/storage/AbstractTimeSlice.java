package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public abstract class AbstractTimeSlice {
    private final long minute;
    private final int second;

    public AbstractTimeSlice(long minute, int second) {
        this.minute = minute;
        this.second = second;
    }

    public long getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }
}
