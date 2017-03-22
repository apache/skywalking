package com.a.eye.skywalking.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The <code>SegmentsMessage</code> is a set of {@link TraceSegment},
 * this set provides a container, when several {@link TraceSegment}s are going to uplink to server.
 *
 *
 * @author wusheng
 */
@JsonAdapter(SegmentsMessage.Serializer.class)
public class SegmentsMessage {
    private List<TraceSegment> segments;

    public SegmentsMessage(){
        segments = new LinkedList<TraceSegment>();
    }

    public void append(TraceSegment segment){
        this.segments.add(segment);
    }

    public List<TraceSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public static class Serializer extends TypeAdapter<SegmentsMessage>{

        @Override
        public void write(JsonWriter out, SegmentsMessage value) throws IOException {
            Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();

            out.beginArray();
            try {
                for (TraceSegment segment : value.segments) {
                    out.jsonValue(gson.toJson(segment));
                }
            }finally {
                out.endArray();
            }
        }

        @Override
        public SegmentsMessage read(JsonReader in) throws IOException {
            SegmentsMessage message = new SegmentsMessage();
            in.beginArray();
            Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
            try {
                while (in.hasNext()) {
                    TraceSegment traceSegment = gson.fromJson(in, TraceSegment.class);
                    message.append(traceSegment);
                }
            } finally {
                in.endArray();
            }
            return message;
        }
    }
}
