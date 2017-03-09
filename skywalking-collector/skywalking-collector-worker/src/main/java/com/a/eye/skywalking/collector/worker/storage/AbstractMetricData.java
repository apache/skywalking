package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public abstract class AbstractMetricData {
    private final String timeMinute;
    private final int timeSecond;

    public AbstractMetricData(String timeMinute, int timeSecond) {
        this.timeMinute = timeMinute;
        this.timeSecond = timeSecond;
    }
}
