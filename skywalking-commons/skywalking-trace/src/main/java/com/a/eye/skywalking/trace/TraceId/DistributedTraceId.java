package com.a.eye.skywalking.trace.TraceId;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/**
 * The <code>DistributedTraceId</code> presents a distributed call chain.
 *
 * This call chain has an unique (service) entrance,
 *
 * such as: Service : http://www.skywalking.com/cust/query, all the services, called behind this service, rest services,
 * db executions, are using the same <code>DistributedTraceId</code> even in different JVM.
 *
 * The <code>DistributedTraceId</code> contains only one string, and can NOT be reset, creating a new instance is the
 * only option.
 *
 * @author wusheng
 */
@JsonAdapter(DistributedTraceId.Serializer.class)
public abstract class DistributedTraceId {
    private String id;

    public DistributedTraceId(String id) {
        this.id = id;
    }

    public String get() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        DistributedTraceId id1 = (DistributedTraceId)o;

        return id != null ? id.equals(id1.id) : id1.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public static class Serializer extends TypeAdapter<DistributedTraceId> {

        @Override
        public void write(JsonWriter out, DistributedTraceId value) throws IOException {
            out.beginArray();
            out.value(value.get());
            out.endArray();
        }

        @Override
        public DistributedTraceId read(JsonReader in) throws IOException {
            in.beginArray();
            PropagatedTraceId traceId = new PropagatedTraceId(in.nextString());
            in.endArray();
            return traceId;
        }
    }
}
