package org.skywalking.apm.collector.stream.worker.impl.data;

/**
 * @author pengys5
 */
public abstract class Exchange {
    private int times;

    public Exchange(int times) {
        this.times = times;
    }

    public void increase() {
        times++;
    }

    public int getTimes() {
        return times;
    }

    public void setTimes(int times) {
        this.times = times;
    }
}
