package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class Segment extends DeserializeObject {
    private String traceSegmentId;
    private long startTime;
    private long endTime;
    private List<TraceSegmentRef> refs;
    private List<Span> spans;
    private String applicationCode;
    private List<GlobalTraceId> relatedGlobalTraces;

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public List<TraceSegmentRef> getRefs() {
        return refs;
    }

    public List<Span> getSpans() {
        return spans;
    }

    public List<GlobalTraceId> getRelatedGlobalTraces() {
        return relatedGlobalTraces;
    }

    public Segment deserialize(JsonReader reader) throws IOException {
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
            } else if (name.equals("ac")) {
                String ac = reader.nextString();
                this.applicationCode = ac;
                JsonBuilder.INSTANCE.append(stringBuilder, "ac", ac, first);
            } else if (name.equals("st")) {
                long st = reader.nextLong();
                this.startTime = st;
                JsonBuilder.INSTANCE.append(stringBuilder, "st", st, first);
            } else if (name.equals("et")) {
                long et = reader.nextLong();
                this.endTime = et;
                JsonBuilder.INSTANCE.append(stringBuilder, "et", et, first);
            } else if (name.equals("rs")) {
                refs = new ArrayList<>();
                reader.beginArray();

                while (reader.hasNext()) {
                    TraceSegmentRef ref = new TraceSegmentRef();
                    ref.deserialize(reader);
                    refs.add(ref);
                }

                reader.endArray();
                JsonBuilder.INSTANCE.append(stringBuilder, "rs", refs, first);
            } else if (name.equals("ss")) {
                spans = new ArrayList<>();
                reader.beginArray();

                while (reader.hasNext()) {
                    Span span = new Span();
                    span.deserialize(reader);
                    spans.add(span);
                }

                reader.endArray();
                JsonBuilder.INSTANCE.append(stringBuilder, "ss", spans, first);
            } else if (name.equals("gt")) {
                relatedGlobalTraces = new ArrayList<>();
                reader.beginArray();

                while (reader.hasNext()) {
                    GlobalTraceId globalTraceId = new GlobalTraceId();
                    globalTraceId.deserialize(reader);
                    relatedGlobalTraces.add(globalTraceId);
                }
                JsonBuilder.INSTANCE.append(stringBuilder, "gt", relatedGlobalTraces, first);

                reader.endArray();
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
