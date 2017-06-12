package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author pengys5
 */
@JsonAdapter(GlobalTraceId.Serializer.class)
public class GlobalTraceId {

    public GlobalTraceId() {
        globalTraceIds = new LinkedList<>();
    }

    private LinkedList<String> globalTraceIds;

    public LinkedList<String> get() {
        return globalTraceIds;
    }

    public static class Serializer extends TypeAdapter<GlobalTraceId> {
        @Override public void write(JsonWriter out, GlobalTraceId value) throws IOException {
            List<String> globalTraceIds = value.globalTraceIds;

            if (globalTraceIds.size() > 0) {
                out.beginArray();
                for (String globalTraceId : globalTraceIds) {
                    out.value(globalTraceId);
                }
                out.endArray();
            }
        }

        @Override public GlobalTraceId read(JsonReader in) throws IOException {
            GlobalTraceId globalTraceId = new GlobalTraceId();
            in.beginArray();
            try {
                while (in.hasNext()) {
                    globalTraceId.get().add(in.nextString());
                }
            } finally {
                in.endArray();
            }
            return globalTraceId;
        }
    }
}
