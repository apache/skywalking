package com.a.eye.skywalking.storage.data.index;

public class IndexMetaInfo {
    private String traceId;

    private String fileName;

    private long offset;

    private int length;

    private long startTime;

    public String getFileName() {
        return fileName;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public long getStartTime() {
        return startTime;
    }
}
