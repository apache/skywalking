package org.skywalking.apm.trace.TraceId;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author wusheng
 */
@JsonAdapter(DistributedTraceIds.Serializer.class)
public class DistributedTraceIds {
    private LinkedList<DistributedTraceId> relatedGlobalTraces;

    public DistributedTraceIds() {
        relatedGlobalTraces = new LinkedList<DistributedTraceId>();
    }

    public List<DistributedTraceId> getRelatedGlobalTraces() {
        return Collections.unmodifiableList(relatedGlobalTraces);
    }

    public void append(DistributedTraceId distributedTraceId) {
        if (relatedGlobalTraces.size() > 0 && relatedGlobalTraces.getFirst() instanceof NewDistributedTraceId) {
            relatedGlobalTraces.removeFirst();
        }
        if (!relatedGlobalTraces.contains(distributedTraceId)) {
            relatedGlobalTraces.add(distributedTraceId);
        }
    }

    public static class Serializer extends TypeAdapter<DistributedTraceIds> {

        @Override
        public void write(JsonWriter out, DistributedTraceIds value) throws IOException {
            List<DistributedTraceId> globalTraces = value.getRelatedGlobalTraces();

            if (globalTraces.size() > 0) {
                out.beginArray();
                for (DistributedTraceId trace : globalTraces) {
                    out.value(trace.get());
                }
                out.endArray();
            }
        }

        @Override
        public DistributedTraceIds read(JsonReader in) throws IOException {
            DistributedTraceIds distributedTraceIds = new DistributedTraceIds();
            in.beginArray();
            try {
                while (in.hasNext()) {
                    PropagatedTraceId traceId = new PropagatedTraceId(in.nextString());
                    distributedTraceIds.append(traceId);
                }
            } finally {
                in.endArray();
            }
            return distributedTraceIds;
        }
    }
}
