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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A request whose points span several minutes is handed to the MAL converters one minute at a time, oldest first,
 * each minute with every point of its own, because a rule folds all the samples of an entity into one value at
 * the first sample's time: a request replaying an hour would otherwise land as one minute.
 */
public class OpenTelemetryMetricRequestProcessorMinuteSplitTest {
    private static final long MINUTE_A = 1_767_225_600_000L;
    private static final long MINUTE_B = MINUTE_A + 60_000L;

    @Test
    @SuppressWarnings("unchecked")
    public void pointsOfOneRequestAreAnalysedMinuteByMinuteOldestFirst() {
        final ModuleManager manager = mock(ModuleManager.class);
        final ModuleProviderHolder holder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder provider = mock(ModuleServiceHolder.class);
        when(manager.find(TelemetryModule.NAME)).thenReturn(holder);
        when(holder.provider()).thenReturn(provider);
        when(provider.getService(MetricsCreator.class)).thenReturn(new MetricsCreatorNoop());
        final OpenTelemetryMetricRequestProcessor processor =
            new OpenTelemetryMetricRequestProcessor(manager, new OtelMetricReceiverConfig());
        final MetricConvert convert = mock(MetricConvert.class);
        processor.addOrReplaceConverter("otel-rules:ai-agent", convert);

        // the later minute is sent first, and a gauge shares the earlier minute with two delta points
        final Metric tokens = Metric.newBuilder()
            .setName("claude_code.token.usage")
            .setSum(Sum.newBuilder()
                       .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
                       .setIsMonotonic(true)
                       .addDataPoints(point(MINUTE_B + 5_000L, 7, "type", "input"))
                       .addDataPoints(point(MINUTE_A + 1_000L, 2, "type", "input"))
                       .addDataPoints(point(MINUTE_A + 59_000L, 50, "type", "output")))
            .build();
        final Metric active = Metric.newBuilder()
            .setName("claude_code.active_time.total")
            .setGauge(Gauge.newBuilder().addDataPoints(point(MINUTE_A + 30_000L, 3, "type", "cli")))
            .build();
        final ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
            .addResourceMetrics(ResourceMetrics.newBuilder()
                                    .setResource(Resource.newBuilder()
                                                     .addAttributes(attribute("service.name", "Claude Code"))
                                                     .addAttributes(attribute("service.layer", "AI_AGENT")))
                                    .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(tokens).addMetrics(active)))
            .build();

        processor.processMetricsRequest(request);

        final ArgumentCaptor<ImmutableMap<String, SampleFamily>> captor = ArgumentCaptor.forClass(ImmutableMap.class);
        verify(convert, times(2)).toMeter(captor.capture());
        final List<ImmutableMap<String, SampleFamily>> passes = captor.getAllValues();

        final ImmutableMap<String, SampleFamily> first = passes.get(0);
        assertEquals(List.of(MINUTE_A + 1_000L, MINUTE_A + 59_000L), timestamps(first.get("claude_code_token_usage")));
        assertEquals(List.of(2.0, 50.0), values(first.get("claude_code_token_usage")));
        assertEquals(List.of(MINUTE_A + 30_000L), timestamps(first.get("claude_code_active_time_total")));
        // the resource's attributes travel on every sample, dots as underscores, so a rule filters on the layer
        final Sample sample = first.get("claude_code_token_usage").samples[0];
        assertEquals("AI_AGENT", sample.getLabels().get("service_layer"));
        assertEquals("Claude Code", sample.getLabels().get("service_name"));
        assertEquals("input", sample.getLabels().get("type"));

        final ImmutableMap<String, SampleFamily> second = passes.get(1);
        assertEquals(List.of(MINUTE_B + 5_000L), timestamps(second.get("claude_code_token_usage")));
        assertEquals(List.of(7.0), values(second.get("claude_code_token_usage")));
        assertEquals(1, second.size());
        verify(convert, times(2)).toMeter(any());
    }

    private static NumberDataPoint point(final long millis, final double value, final String key, final String label) {
        return NumberDataPoint.newBuilder()
                              .setTimeUnixNano(millis * 1_000_000L)
                              .setAsDouble(value)
                              .addAttributes(attribute(key, label))
                              .build();
    }

    private static KeyValue attribute(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    private static List<Long> timestamps(final SampleFamily family) {
        return Stream.of(family.samples).map(Sample::getTimestamp).collect(Collectors.toList());
    }

    private static List<Double> values(final SampleFamily family) {
        return Stream.of(family.samples).map(Sample::getValue).collect(Collectors.toList());
    }
}
