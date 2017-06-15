package org.skywalking.apm.collector.worker.instance.util;

public class IDSequence {
    private long startInstanceId;
    private long endInstanceId;

    IDSequence(long start, long end) {
        this.startInstanceId = start;
        this.endInstanceId = end;
    }

    public long nextInstanceId() {
        return startInstanceId++;
    }

    public boolean hasNext() {
        return startInstanceId + 1 < endInstanceId;
    }
}
