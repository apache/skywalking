package com.a.eye.skywalking.collector.worker.segment.entity;

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

    public TraceSegmentRef deserialize(SegmentJsonReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");

        boolean first = true;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "ts":
                    String ts = reader.nextString().getValue();
                    this.traceSegmentId = ts;
                    JsonBuilder.INSTANCE.append(stringBuilder, "ts", ts, first);
                    break;
                case "si":
                    Integer si = reader.nextInt();
                    this.spanId = si;
                    JsonBuilder.INSTANCE.append(stringBuilder, "si", si, first);
                    break;
                case "ac":
                    String ac = reader.nextString().getValue();
                    this.applicationCode = ac;
                    JsonBuilder.INSTANCE.append(stringBuilder, "ac", ac, first);
                    break;
                case "ph":
                    String ph = reader.nextString().getValue();
                    this.peerHost = ph;
                    JsonBuilder.INSTANCE.append(stringBuilder, "ph", ph, first);
                    break;
                default:
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
