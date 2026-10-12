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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanMetadataOfTest {
    private static final long START_MILLIS = 1_700_000_000_390L;
    private static final long END_MILLIS = 1_700_000_000_500L;

    @Test
    void aMissingTimeIsReplacedByTheOtherOne() {
        final SpanMetadata noStart = metaOf(0L, TimeUnit.MILLISECONDS.toNanos(END_MILLIS));
        assertEquals(END_MILLIS, noStart.getStartTime(), "not the epoch");
        assertEquals(0, noStart.getLatency(), "not decades");

        final SpanMetadata noEnd = metaOf(TimeUnit.MILLISECONDS.toNanos(START_MILLIS), 0L);
        assertEquals(START_MILLIS, noEnd.getTimestamp());
        assertEquals(0, noEnd.getLatency());
    }

    @Test
    void aSpanWithNoTimeAtAllIsPlacedWhenItArrived() {
        final long before = System.currentTimeMillis();
        final SpanMetadata meta = metaOf(0L, 0L);
        assertTrue(meta.getStartTime() >= before, String.valueOf(meta.getStartTime()));
        assertEquals(meta.getStartTime(), meta.getTimestamp());
    }

    @Test
    void bothTimesAreKeptWhenPresent() {
        final SpanMetadata meta = metaOf(TimeUnit.MILLISECONDS.toNanos(START_MILLIS),
            TimeUnit.MILLISECONDS.toNanos(END_MILLIS));
        assertEquals(START_MILLIS, meta.getStartTime());
        assertEquals(END_MILLIS, meta.getTimestamp());
        assertEquals(110, meta.getLatency());
    }

    private static SpanMetadata metaOf(final long startNanos, final long endNanos) {
        final Span span = Span.newBuilder().setStartTimeUnixNano(startNanos).setEndTimeUnixNano(endNanos).build();
        return OpenTelemetryTraceHandler.spanMetadataOf(span, "frontend", Collections.emptyMap(), "", "");
    }
}
