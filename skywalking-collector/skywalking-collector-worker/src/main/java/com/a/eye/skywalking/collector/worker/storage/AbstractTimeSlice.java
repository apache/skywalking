package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public abstract class AbstractTimeSlice {
    private final long minute;
    private final long hour;
    private final long day;
    private final int second;

    public AbstractTimeSlice(long minute, long hour, long day, int second) {
        this.minute = minute;
        this.hour = hour;
        this.day = day;
        this.second = second;
    }

    public long getMinute() {
        return minute;
    }

    public long getHour() {
        return hour;
    }

    public long getDay() {
        return day;
    }

    public int getSecond() {
        return second;
    }
}
