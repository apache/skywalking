package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;
import java.io.IOException;

/**
 * @author pengys5
 */
public class GlobalTraceId extends DeserializeObject {
    private String globalTraceId;

    public String get() {
        return globalTraceId;
    }

    public GlobalTraceId deserialize(JsonReader reader) throws IOException {
        this.globalTraceId = reader.nextString();
        this.setJsonStr("\"" + globalTraceId + "\"");
        return this;
    }
}
