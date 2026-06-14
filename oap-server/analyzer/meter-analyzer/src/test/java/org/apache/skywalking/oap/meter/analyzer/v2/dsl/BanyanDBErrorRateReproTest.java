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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.counter.CounterWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproduces the BanyanDB liaison_grpc_error_rate fabrication using the EXACT rule expression and the
 * real (frozen) counter values scraped from the live demo FODC proxy. Counters never change across the
 * simulated scrapes, so every rate term — and the summed result — MUST be 0. Any non-zero output proves
 * the CounterWindow key collision: the three distinct error counters reduce to identical labels after
 * .sum([...]) and, because the rate keys on the (shared, rule-level) context.metricName instead of each
 * counter's own name, they share one CounterWindow slot and rate against each other's values.
 */
public class BanyanDBErrorRateReproTest {

    private static final String GROUP_BY = "['cluster','pod_name','container_name','node_role','node_type']";

    // Verbatim from otel-rules/banyandb/banyandb-instance.yaml : liaison_grpc_error_rate (value part).
    private static final String EXPR =
        "(banyandb_liaison_grpc_total_err.sum(" + GROUP_BY + ").rate('PT1M')"
        + " + banyandb_liaison_grpc_total_registry_err.sum(" + GROUP_BY + ").rate('PT1M')"
        + " + banyandb_liaison_grpc_total_stream_msg_received_err.sum(" + GROUP_BY + ").rate('PT1M')) * 60";

    @BeforeEach
    void resetWindow() {
        CounterWindow.INSTANCE.reset();
    }

    private static Sample s(final String name, final double value, final long ts, final String... kv) {
        final ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
        for (int i = 0; i < kv.length; i += 2) {
            b.put(kv[i], kv[i + 1]);
        }
        return Sample.builder().name(name).labels(b.build()).value(value).timestamp(ts).build();
    }

    // The three liaison-1 families, with the real frozen values (total_err=5, registry_err=166, stream=5).
    // node_type is intentionally ABSENT on liaison samples, exactly as the FODC proxy exposes them.
    private Map<String, SampleFamily> scrape(final long ts) {
        final String[] common = {
            "cluster", "showcase-banyandb",
            "pod_name", "demo-banyandb-liaison-1",
            "container_name", "liaison",
            "node_role", "ROLE_LIAISON",
        };
        final List<Sample> totalErr = new ArrayList<>();
        totalErr.add(s("banyandb_liaison_grpc_total_err", 1, ts, with(common, "service", "measure", "method", "query", "group", "sw_metadata")));
        totalErr.add(s("banyandb_liaison_grpc_total_err", 2, ts, with(common, "service", "measure", "method", "query", "group", "sw_metricsMinute")));
        totalErr.add(s("banyandb_liaison_grpc_total_err", 1, ts, with(common, "service", "measure", "method", "query", "group", "sw_metricsHour")));
        totalErr.add(s("banyandb_liaison_grpc_total_err", 1, ts, with(common, "service", "measure", "method", "query", "group", "sw_metricsDay")));

        final List<Sample> registryErr = new ArrayList<>();
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 47, ts, with(common, "service", "measure", "method", "get", "group", "sw_metricsHour")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 47, ts, with(common, "service", "measure", "method", "get", "group", "sw_metricsMinute")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 47, ts, with(common, "service", "measure", "method", "get", "group", "sw_metricsDay")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 7, ts, with(common, "service", "indexRule", "method", "create", "group", "sw_metricsDay")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 7, ts, with(common, "service", "indexRule", "method", "create", "group", "sw_metricsHour")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 7, ts, with(common, "service", "indexRule", "method", "create", "group", "sw_metricsMinute")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 2, ts, with(common, "service", "trace", "method", "get", "group", "sw_trace")));
        registryErr.add(s("banyandb_liaison_grpc_total_registry_err", 2, ts, with(common, "service", "trace", "method", "get", "group", "sw_zipkinTrace")));

        final List<Sample> streamErr = new ArrayList<>();
        streamErr.add(s("banyandb_liaison_grpc_total_stream_msg_received_err", 1, ts, with(common, "service", "measure", "method", "write", "group", "sw_metadata")));
        streamErr.add(s("banyandb_liaison_grpc_total_stream_msg_received_err", 2, ts, with(common, "service", "trace", "method", "write", "group", "sw_trace")));
        streamErr.add(s("banyandb_liaison_grpc_total_stream_msg_received_err", 2, ts, with(common, "service", "stream", "method", "write", "group", "sw_recordsLog")));

        final Map<String, SampleFamily> map = new HashMap<>();
        map.put("banyandb_liaison_grpc_total_err", SampleFamilyBuilder.newBuilder(totalErr.toArray(new Sample[0])).build());
        map.put("banyandb_liaison_grpc_total_registry_err", SampleFamilyBuilder.newBuilder(registryErr.toArray(new Sample[0])).build());
        map.put("banyandb_liaison_grpc_total_stream_msg_received_err", SampleFamilyBuilder.newBuilder(streamErr.toArray(new Sample[0])).build());
        return map;
    }

    private static String[] with(final String[] common, final String... extra) {
        final String[] out = new String[common.length + extra.length];
        System.arraycopy(common, 0, out, 0, common.length);
        System.arraycopy(extra, 0, out, common.length, extra.length);
        return out;
    }

    @Test
    void unchangedCounters_errorRate_mustBeZero() {
        final Expression expr = DSL.parse("meter_banyandb_instance_liaison_grpc_error_rate", EXPR);
        long ts = 1_700_000_000_000L;
        final long step = 10_000L; // 10s scrape, matching the showcase collector
        for (int scrape = 0; scrape < 6; scrape++, ts += step) {
            final Result result = expr.run(scrape(ts));
            double maxAbs = 0.0;
            if (result.isSuccess() && result.getData() != SampleFamily.EMPTY) {
                for (final Sample out : result.getData().samples) {
                    maxAbs = Math.max(maxAbs, Math.abs(out.getValue()));
                }
            }
            // Counters never changed -> error rate MUST be 0 on every scrape.
            assertEquals(0.0, maxAbs, 1e-9,
                "Unchanged counters must yield 0 error rate, but scrape " + scrape + " produced " + maxAbs);
        }
    }
}
