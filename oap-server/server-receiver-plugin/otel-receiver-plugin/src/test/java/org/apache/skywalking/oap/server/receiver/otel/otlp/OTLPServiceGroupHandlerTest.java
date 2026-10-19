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
import java.util.stream.Collectors;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The trace analysis and the store see the grouped service name; the resource attributes stay as sent.
 */
class OTLPServiceGroupHandlerTest {

    @Test
    void theAnalysisSeesTheServiceUnderTheGroupTheFirstPresentKeyNames() {
        final List<SpanMetadata> analyzed = new ArrayList<>();
        final OpenTelemetryTraceHandler handler = handler("service.namespace,k8s.namespace.name", analyzed, false);

        handler.processExport(request(
            Map.of("service.name", "cart", "service.namespace", "opentelemetry-demo", "k8s.namespace.name", "otel-demo"),
            Map.of("service.name", "billing", "k8s.namespace.name", "finance"),
            Map.of("service.name", "legacy")));

        assertEquals(List.of("opentelemetry-demo::cart", "finance::billing", "legacy"),
            analyzed.stream().map(SpanMetadata::getService).collect(Collectors.toList()));
    }

    @Test
    void withoutTheSettingTheServiceKeepsItsName() {
        final List<SpanMetadata> analyzed = new ArrayList<>();
        final OpenTelemetryTraceHandler handler = handler("", analyzed, false);

        handler.processExport(request(Map.of("service.name", "cart", "service.namespace", "opentelemetry-demo")));

        assertEquals("cart", analyzed.get(0).getService());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theStoreFilesTheSpanUnderTheGroupedServiceAndKeepsItsAttributes() throws Exception {
        final OpenTelemetryTraceHandler handler = handler("service.namespace", new ArrayList<>(), true);
        final OTLPSpanForward forward = mock(OTLPSpanForward.class);
        when(forward.send(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        final Field field = OpenTelemetryTraceHandler.class.getDeclaredField("otlpSpanForward");
        field.setAccessible(true);
        field.set(handler, forward);

        handler.processExport(request(Map.of("service.name", "cart", "service.namespace", "opentelemetry-demo")));

        final ArgumentCaptor<String> service = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Map<String, String>> resourceTags = ArgumentCaptor.forClass(Map.class);
        verify(forward).send(any(), any(), any(), any(), any(), service.capture(), resourceTags.capture(), any(),
            anyBoolean());
        assertEquals("opentelemetry-demo::cart", service.getValue(), "the TraceQL service is the analyzed one");
        assertEquals("cart", resourceTags.getValue().get("service.name"), "the attribute stays as sent");
    }

    private static OpenTelemetryTraceHandler handler(final String groupKeys, final List<SpanMetadata> analyzed,
                                                     final boolean persist) {
        final ITraceAnalyzerService analyzer = mock(ITraceAnalyzerService.class);
        // Unless persisted, the span stops after the analysis and no storage is needed.
        when(analyzer.doAnalysis(any(), any(), any(), any())).thenAnswer(inv -> {
            analyzed.add(inv.getArgument(0));
            return SpanAnalysisResult.builder().persist(persist).build();
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
        when(listeners.notifyOTLPPhase(any(), any(), any(), any())).thenReturn(SpanListenerResult.CONTINUE);
        when(listeners.notifyNativeOTLPPhase(any(), any(), any(), any())).thenReturn(SpanListenerResult.CONTINUE);
        provide(manager, CoreModule.NAME, SpanListenerManager.class, listeners);
        provide(manager, TelemetryModule.NAME, MetricsCreator.class, metricsCreator);

        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpServiceGroupKeys(groupKeys);
        final OpenTelemetryTraceHandler handler = new OpenTelemetryTraceHandler();
        handler.init(manager, config);
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
                    .setName("GET /cart")
                    .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                    .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                    .setEndTimeUnixNano(1_700_000_000_100_000_000L))));
        }
        return request.build();
    }
}
