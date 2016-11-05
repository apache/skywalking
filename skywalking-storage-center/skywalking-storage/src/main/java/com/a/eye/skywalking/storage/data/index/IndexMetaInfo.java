package com.a.eye.skywalking.storage.data.index;

public class IndexMetaInfo {
    private String traceId;

    private String fileName;

    private long offset;

    private int length;

    private long startTime;

    public IndexMetaInfo(String fileName, long offset, int length) {
        this.fileName = fileName;
        this.offset = offset;
        this.length = length;
    }

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

    public String getTraceId() {
        return null;
    }

    public String getParentLevelId() {
        return null;
    }

    public int getLevelId() {
        return 0;
    }
}
