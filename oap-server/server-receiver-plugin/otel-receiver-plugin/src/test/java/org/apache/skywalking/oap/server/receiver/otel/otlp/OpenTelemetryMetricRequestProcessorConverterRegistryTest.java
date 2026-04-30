/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * Tests the {@link org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry}
 * behaviour that {@link OpenTelemetryMetricRequestProcessor} exposes. We go through the
 * public interface only — add/replace/remove + toMeter are the operator-visible contract.
 *
 * <p>We don't exercise the processMetricsRequest OTLP path here because that pulls in
 * proto-message fixtures the existing integration tests cover. What we target is the
 * hot-update concurrency contract: registry mutations don't NPE ingest, and
 * removeConverter on an absent key is a clean no-op (so {@code /delete} never errors
 * out on an already-torn-down peer).
 */
class OpenTelemetryMetricRequestProcessorConverterRegistryTest {

    @Test
    void addOrReplaceThenRemoveRoundTrips() {
        final OpenTelemetryMetricRequestProcessor proc = newProcessor();
        final MetricConvert convert = mock(MetricConvert.class);

        // Add, replace with a different converter, then remove. Each call must be side-effect
        // free beyond its intended mutation — addOrReplaceConverter never throws, even when
        // the same key is rebound back-to-back (runtime-rule FILTER_ONLY swap re-uses the
        // existing key, so this is the hot path).
        proc.addOrReplaceConverter("otel-rules:vm", convert);
        proc.addOrReplaceConverter("otel-rules:vm", mock(MetricConvert.class));
        proc.removeConverter("otel-rules:vm");
    }

    @Test
    void removeConverterOnAbsentKeyIsIdempotent() {
        // /delete on a bundle this peer already tore down (out-of-order tick firing) must
        // not raise. The reconciler calls dropRuntimeMalConverter defensively; if the key is
        // missing that's "already converged", not a failure.
        final OpenTelemetryMetricRequestProcessor proc = newProcessor();

        assertDoesNotThrow(() -> proc.removeConverter("otel-rules:nonexistent"));
    }

    @Test
    void toMeterDoesNotThrowWithNoConverters() {
        // Fresh processor — converters map is empty. Samples arriving here must be silently
        // dropped (not NPE) so SpanListener code that feeds MetricKit samples via toMeter
        // doesn't have to guard for "runtime-rule not wired yet".
        final OpenTelemetryMetricRequestProcessor proc = newProcessor();

        assertDoesNotThrow(() -> proc.toMeter(com.google.common.collect.ImmutableMap.of()));
    }

    private static OpenTelemetryMetricRequestProcessor newProcessor() {
        return new OpenTelemetryMetricRequestProcessor(mock(ModuleManager.class),
            mock(OtelMetricReceiverConfig.class));
    }
}
