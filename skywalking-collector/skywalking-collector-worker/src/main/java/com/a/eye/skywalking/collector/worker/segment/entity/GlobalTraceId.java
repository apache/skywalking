package com.a.eye.skywalking.collector.worker.segment.entity;

import java.io.IOException;

/**
 * @author pengys5
 */
public class GlobalTraceId extends DeserializeObject {
    private String globalTraceId;

    public String get() {
        return globalTraceId;
    }

    public GlobalTraceId deserialize(SegmentJsonReader reader) throws IOException {
        this.globalTraceId = reader.nextString().getNonQuoteValue();
        this.setJsonStr("\"" + globalTraceId + "\"");
        return this;
    }
}
