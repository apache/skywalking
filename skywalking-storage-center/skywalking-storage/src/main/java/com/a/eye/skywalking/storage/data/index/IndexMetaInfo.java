package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.data.file.DataFileNameDesc;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanType;

public class IndexMetaInfo {

    private SpanData spanData;

    private DataFileNameDesc nameDesc;

    private long offset;

    private int length;

    public IndexMetaInfo(SpanData data, DataFileNameDesc fileNameDesc, long offset, int length) {
        this.spanData = data;
        this.nameDesc = fileNameDesc;
        this.offset = offset;
        this.length = length;
    }

    public DataFileNameDesc getFileName() {
        return nameDesc;
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

    public Long[] getTraceId() {
        return spanData.getTraceIdSegments();
    }

    public String getLevelId() {
        return spanData.getLevelId();
    }

    public SpanType getSpanType() {
        return spanData.getSpanType();
    }
}
