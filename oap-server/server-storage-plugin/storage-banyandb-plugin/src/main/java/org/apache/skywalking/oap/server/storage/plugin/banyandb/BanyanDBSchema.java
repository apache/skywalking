/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BanyanDBSchema {
    public static final String NAME = "sw";
    public static final String GROUP = "default";
    public static final List<String> FIELD_NAMES;

    public static final Set<String> INDEX_FIELDS = ImmutableSet.of("http.method", "status_code", "db.type",
            "db.instance", "mq.queue", "mq.topic", "mq.broker");

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
