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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code trace_in_latency} counts spans: one observation per span at the average latency of the spans processed
 * together, and none for a segment without spans.
 */
class TraceInLatencyTest {
    private final List<Double> observed = new ArrayList<>();
    private final HistogramMetrics histogram = new HistogramMetrics() {
        @Override
        public void observe(final double value) {
            observed.add(value);
        }
    };

    @Test
    void eachSpanIsObservedAtTheAverageTimesTheScale() {
        final long startNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(4);

        TraceInLatency.observeSpans(histogram, startNanos, 4);

        assertEquals(4, observed.size(), "one observation per span");
        final double first = observed.get(0);
        observed.forEach(value -> assertEquals(first, value, "every span is observed at the same average"));
        // 4 spans in at least 4 ms: at least 1 ms each, observed as at least 1 s per 1,000 spans.
        assertTrue(first >= 1.0, "observed " + first);
    }

    @Test
    void aSegmentWithoutSpansIsNotObserved() {
        TraceInLatency.observeSpans(histogram, System.nanoTime(), 0);

        assertTrue(observed.isEmpty());
    }
}
