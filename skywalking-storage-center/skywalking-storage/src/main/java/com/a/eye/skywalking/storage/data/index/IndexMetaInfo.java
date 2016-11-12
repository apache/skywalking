package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanType;

public class IndexMetaInfo {

    private SpanData spanData;

    private String fileName;

    private long offset;

    private int length;

    public IndexMetaInfo(SpanData data, String fileName, long offset, int length) {
        this.spanData = data;
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

    public long getTraceStartTime() {
        return spanData.getTraceStartTime();
    }

    public String getTraceId() {
        return spanData.getTraceId();
    }

    public String getLevelId() {
        return spanData.getLevelId();
    }

    public SpanType getSpanType() {
        return spanData.getSpanType();
    }
}
