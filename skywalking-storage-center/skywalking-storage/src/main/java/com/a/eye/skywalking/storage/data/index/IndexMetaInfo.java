package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.data.file.DataFileNameDesc;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanType;

public class IndexMetaInfo {

    private SpanData spanData;

    private SpanType spanType;

    private DataFileNameDesc nameDesc;

    private long offset;

    private int length;

    public IndexMetaInfo(SpanData data, DataFileNameDesc fileNameDesc, long offset, int length) {
        this.spanData = data;
        this.nameDesc = fileNameDesc;
        this.spanType = data.getSpanType();
        this.offset = offset;
        this.length = length;
    }

    public IndexMetaInfo(DataFileNameDesc fileNameDesc, long offset, int length, SpanType spanType) {
        this.nameDesc = fileNameDesc;
        this.offset = offset;
        this.length = length;
        this.spanType = spanType;
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
        return spanType;
    }

    @Override
    public String toString() {
        return "IndexMetaInfo{" +
                "spanType=" + spanType +
                ", nameDesc=" + nameDesc +
                ", offset=" + offset +
                ", length=" + length +
                '}';
    }
}
