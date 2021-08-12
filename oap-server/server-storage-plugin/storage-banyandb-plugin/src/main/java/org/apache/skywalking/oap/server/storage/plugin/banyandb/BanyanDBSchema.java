package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.collect.ImmutableList;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BanyanDBSchema {
    public static final String name = "sw";
    public static final String group = "default";
    public static final List<String> FIELD_NAMES;

    static {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("trace_id");
        fields.add("state");
        fields.add("service_id");
        fields.add("service_instance_id");
        fields.add("endpoint_id");
        fields.add("duration");
        fields.add("start_time");
        fields.add("http.method");
        fields.add("status_code");
        fields.add("db.type");
        fields.add("db.instance");
        fields.add("mq.queue");
        fields.add("mq.topic");
        fields.add("mq.broker");
        FIELD_NAMES = ImmutableList.copyOf(fields);
    }

    public enum TraceState {
        ALL(0), SUCCESS(1), ERROR(2);

        @Getter
        private final int state;

        TraceState(int state) {
            this.state = state;
        }
    }
}
