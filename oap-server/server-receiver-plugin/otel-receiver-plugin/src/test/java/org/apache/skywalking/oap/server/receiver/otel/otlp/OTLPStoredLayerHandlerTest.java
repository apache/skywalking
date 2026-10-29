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

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stored span carries the layer the service catalog files its service under, so a search on it agrees with
 * the catalog; the trace analysis reads the layer as sent.
 */
class OTLPStoredLayerHandlerTest {

    @Test
    void anUnregisteredOrAbsentLayerIsStoredAsUndefined() throws Exception {
        final List<SpanMetadata> analyzed = new ArrayList<>();
        final OpenTelemetryTraceHandler handler = handler(analyzed);
        final OTLPSpanForward forward = mock(OTLPSpanForward.class);
        when(forward.send(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        final Field field = OpenTelemetryTraceHandler.class.getDeclaredField("otlpSpanForward");
        field.setAccessible(true);
        field.set(handler, forward);

        handler.processExport(request(
            Map.of("service.name", "typo", "service.layer", "otlp_general"),
            Map.of("service.name", "unlabelled"),
            Map.of("service.name", "general", "service.layer", "OTLP_GENERAL")));

        assertEquals(List.of("otlp_general", "", "OTLP_GENERAL"),
            List.of(analyzed.get(0).getLayer(), analyzed.get(1).getLayer(), analyzed.get(2).getLayer()),
            "the analysis reads the layer as sent");
        final ArgumentCaptor<Resource> stored = ArgumentCaptor.forClass(Resource.class);
        verify(forward, times(3)).send(stored.capture(), any(), any(), any(), any(), any(), any(), any(),
            anyBoolean());
        assertEquals(List.of("UNDEFINED", "UNDEFINED", "OTLP_GENERAL"),
            List.of(layerOf(stored.getAllValues().get(0)), layerOf(stored.getAllValues().get(1)),
                    layerOf(stored.getAllValues().get(2))));
    }

    private static String layerOf(final Resource resource) {
        return resource.getAttributesList().stream()
                       .filter(kv -> "service.layer".equals(kv.getKey()))
                       .map(kv -> kv.getValue().getStringValue())
                       .findFirst()
                       .orElse(null);
    }

    private static OpenTelemetryTraceHandler handler(final List<SpanMetadata> analyzed) {
        final ITraceAnalyzerService analyzer = mock(ITraceAnalyzerService.class);
        when(analyzer.doAnalysis(any(), any(), any(), any())).thenAnswer(inv -> {
            analyzed.add(inv.getArgument(0));
            return SpanAnalysisResult.builder().persist(true).build();
        });
        final MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createHistogramMetric(anyString(), anyString(), any(), any()))
            .thenReturn(new HistogramMetrics() {
                @Override
                public void observe(final double value) {
                }
            });
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.has(TraceAnalyzerModule.NAME)).thenReturn(true);
        provide(manager, TraceAnalyzerModule.NAME, ITraceAnalyzerService.class, analyzer);
        final SpanListenerManager listeners = mock(SpanListenerManager.class);
        when(listeners.notifyOTLPPhase(any(), any(), any(), any(), any())).thenReturn(SpanListenerResult.CONTINUE);
        when(listeners.notifyNativeOTLPPhase(any(), any(), any(), any(), any())).thenReturn(SpanListenerResult.CONTINUE);
        provide(manager, CoreModule.NAME, SpanListenerManager.class, listeners);
        provide(manager, TelemetryModule.NAME, MetricsCreator.class, metricsCreator);

        final OpenTelemetryTraceHandler handler = new OpenTelemetryTraceHandler();
        handler.init(manager, new OtelMetricReceiverConfig());
        return handler;
    }

    private static <T extends Service> void provide(final ModuleManager manager, final String module,
                                                    final Class<T> type, final T service) {
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(type)).thenReturn(service);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        when(manager.find(module)).thenReturn(provider);
    }

    @SafeVarargs
    private static ExportTraceServiceRequest request(final Map<String, String>... resources) {
        final ExportTraceServiceRequest.Builder request = ExportTraceServiceRequest.newBuilder();
        for (final Map<String, String> attributes : resources) {
            final Resource.Builder resource = Resource.newBuilder();
            attributes.forEach((k, v) -> resource.addAttributes(
                KeyValue.newBuilder().setKey(k).setValue(AnyValue.newBuilder().setStringValue(v))));
            request.addResourceSpans(ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(ScopeSpans.newBuilder().addSpans(Span.newBuilder()
                    .setTraceId(ByteString.copyFrom(new byte[16]))
                    .setSpanId(ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
                    .setName("GET /")
                    .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                    .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                    .setEndTimeUnixNano(1_700_000_000_100_000_000L))));
        }
        return request.build();
    }
}
