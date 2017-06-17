package org.skywalking.apm.collector.worker.instance.util;

public class IdentificationSegment {
    private long startInstanceId;
    private long endInstanceId;

    IdentificationSegment(long start, long end) {
        this.startInstanceId = start;
        this.endInstanceId = end;
    }

    public long nextInstanceId() {
        return startInstanceId++;
    }

    public boolean hasNext() {
        return startInstanceId + 1 >= endInstanceId;
    }
}
