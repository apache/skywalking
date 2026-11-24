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

package org.apache.skywalking.oap.server.core.trace;

import java.util.EnumMap;
import java.util.Map;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * The {@code spans_dropped_count} counters of one trace protocol: the spans received but not stored, by
 * {@link Reason}. The Zipkin and OTLP trace receivers share the metric, so every registration carries the same
 * {@code protocol} and {@code reason} labels; the Prometheus telemetry fixes a metric's label names on its first
 * registration. A span the span listeners or a TAL rule chose not to persist is not dropped.
 */
public final class TraceSpansDropped {
    public static final String NAME = "spans_dropped_count";
    public static final String HELP = "The count of trace spans received but not stored, by protocol and reason: "
        + "sampled (head sampling), rate_limited (the spans per second limit), store_failed (an error while storing "
        + "the span), process_failed (an error while processing the span; only that span is lost)";

    public enum Reason {
        SAMPLED("sampled"),
        RATE_LIMITED("rate_limited"),
        STORE_FAILED("store_failed"),
        PROCESS_FAILED("process_failed");

        private final String label;

        Reason(final String label) {
            this.label = label;
        }
    }

    private final Map<Reason, CounterMetrics> counters = new EnumMap<>(Reason.class);

    /**
     * @param protocol the {@code protocol} label, as the receiver's {@code trace_in_latency} carries it
     */
    public TraceSpansDropped(final MetricsCreator metricsCreator, final String protocol) {
        for (final Reason reason : Reason.values()) {
            counters.put(reason, metricsCreator.createCounter(
                NAME, HELP,
                new MetricsTag.Keys("protocol", "reason"),
                new MetricsTag.Values(protocol, reason.label)
            ));
        }
    }

    public void count(final Reason reason, final long count) {
        counters.get(reason).inc(count);
    }
}
