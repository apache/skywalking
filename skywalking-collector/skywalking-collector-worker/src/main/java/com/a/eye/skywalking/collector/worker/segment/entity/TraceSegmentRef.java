package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;

import java.io.IOException;

/**
 * @author pengys5
 */
public class TraceSegmentRef extends DeserializeObject {

    private String traceSegmentId;

    private int spanId = -1;

    private String applicationCode;

    private String peerHost;

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public TraceSegmentRef deserialize(JsonReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");

        boolean first = true;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("ts")) {
                String ts = reader.nextString();
                this.traceSegmentId = ts;
                JsonBuilder.INSTANCE.append(stringBuilder, "ts", ts, first);
            } else if (name.equals("si")) {
                Integer si = reader.nextInt();
                this.spanId = si;
                JsonBuilder.INSTANCE.append(stringBuilder, "si", si, first);
            } else if (name.equals("ac")) {
                String ac = reader.nextString();
                this.applicationCode = ac;
                JsonBuilder.INSTANCE.append(stringBuilder, "ac", ac, first);
            } else if (name.equals("ph")) {
                String ph = reader.nextString();
                this.peerHost = ph;
                JsonBuilder.INSTANCE.append(stringBuilder, "ph", ph, first);
            } else {
                reader.skipValue();
            }
            first = false;
        }
        reader.endObject();

        stringBuilder.append("}");
        this.setJsonStr(stringBuilder.toString());
        return this;
    }
}
