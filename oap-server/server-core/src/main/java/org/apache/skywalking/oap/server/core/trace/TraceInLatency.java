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
 */

package org.apache.skywalking.oap.server.core.trace;

import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

/**
 * The {@code trace_in_latency} histogram, registered by every trace receiver with its own {@code protocol} label.
 * The Prometheus telemetry keeps the help text of the first registration for the whole metric (see
 * {@code BaseMetrics#getMetric}), so every registration uses {@link #HELP}, which describes all the protocols.
 */
public final class TraceInLatency {
    public static final String NAME = "trace_in_latency";

    /**
     * One span takes microseconds, below the smallest default histogram bucket (5ms), so a per-span latency is
     * observed as the time this many such spans would take. The default buckets (5ms to 10s) then cover 5us to 10ms
     * per span; a larger scale pushes an ordinary millisecond span past the top bucket, whose percentile MAL reads
     * as {@code Long.MAX_VALUE}.
     */
    public static final int SPAN_LATENCY_SCALE = 1_000;

    public static final String HELP = "The process latency of trace data, by protocol: one observation per span, the "
        + "span's seconds times " + SPAN_LATENCY_SCALE + ", i.e. the time " + SPAN_LATENCY_SCALE + " such spans take, "
        + "so the count is spans. grpc, http and kafka (SkyWalking agent): a segment is processed as a whole, so each "
        + "of its spans is observed at the segment's latency divided by its span count, and a segment without spans "
        + "counts once. zipkin-http and zipkin-kafka: each span on its own; sampled-out spans are not included. "
        + "otlp-grpc and otlp-http: each span on its own, covering the span listeners, the TAL rules and the native "
        + "store or the conversion to Zipkin; spans of a resource without a service name are not included. Decoding "
        + "the request or message is not included.";

    private TraceInLatency() {
    }

    /**
     * Observe {@code spanCount} spans processed together since {@code startNanos}, each at their average latency
     * times {@link #SPAN_LATENCY_SCALE}. One observation per span keeps the histogram's count in spans and its sum
     * the real time; a {@code spanCount} below 1 is observed as one span.
     *
     * @param histogram  the {@code trace_in_latency} histogram of the receiving protocol.
     * @param startNanos the {@link System#nanoTime()} taken before the spans were processed.
     * @param spanCount  the number of spans processed.
     */
    public static void observeSpans(final HistogramMetrics histogram, final long startNanos, final int spanCount) {
        final int spans = Math.max(spanCount, 1);
        final double perSpan = (System.nanoTime() - startNanos) / 1.0E9D / spans * SPAN_LATENCY_SCALE;
        for (int i = 0; i < spans; i++) {
            histogram.observe(perSpan);
        }
    }
}
