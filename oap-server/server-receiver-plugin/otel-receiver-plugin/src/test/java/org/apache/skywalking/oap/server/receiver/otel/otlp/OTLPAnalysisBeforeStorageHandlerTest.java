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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The TAL rules see every span of a natively stored export, including one a span listener keeps out of storage, so
 * what a rule counts or samples, an AI evaluation among them, does not depend on whether the span is stored.
 */
class OTLPAnalysisBeforeStorageHandlerTest {

    @Test
    void theRulesRunOnASpanAListenerKeepsOutOfStorage() throws Exception {
        final List<SpanMetadata> analyzed = new ArrayList<>();
        final SpanListenerManager listeners = mock(SpanListenerManager.class);
        // The first span is vetoed, the second is not.
        when(listeners.notifyOTLPPhase(any(), any(), any(), any(), any())).thenReturn(
            SpanListenerResult.builder().shouldPersist(false).build(), SpanListenerResult.CONTINUE);
        final OpenTelemetryTraceHandler handler = handler(analyzed, listeners);
        final OTLPSpanForward forward = mock(OTLPSpanForward.class);
        when(forward.send(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        final Field field = OpenTelemetryTraceHandler.class.getDeclaredField("otlpSpanForward");
        field.setAccessible(true);
        field.set(handler, forward);

        handler.processExport(request("vetoed", "kept"));

        assertEquals(List.of("0102030405060701", "0102030405060702"),
            analyzed.stream().map(SpanMetadata::getSpanId).collect(Collectors.toList()), "both spans reach the rules");
        final ArgumentCaptor<Span> stored = ArgumentCaptor.forClass(Span.class);
        verify(forward, times(1)).send(any(), any(), any(), any(), stored.capture(), any(), any(), any(),
            anyBoolean());
        assertEquals("kept", stored.getValue().getName(), "only the span no listener vetoed is stored");
    }

    private static OpenTelemetryTraceHandler handler(final List<SpanMetadata> analyzed,
                                                     final SpanListenerManager listeners) {
        final ITraceAnalyzerService analyzer = mock(ITraceAnalyzerService.class);
        when(analyzer.doAnalysis(any(), any(), any(), any())).thenAnswer(inv -> {
            analyzed.add(inv.getArgument(0));
            return SpanAnalysisResult.CONTINUE;
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

    private static ExportTraceServiceRequest request(final String... spanNames) {
        final ScopeSpans.Builder scope = ScopeSpans.newBuilder();
        byte last = 1;
        for (final String name : spanNames) {
            scope.addSpans(Span.newBuilder()
                .setTraceId(ByteString.copyFrom(new byte[16]))
                .setSpanId(ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6, 7, last++}))
                .setName(name)
                .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
                .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                .setEndTimeUnixNano(1_700_000_000_100_000_000L));
        }
        return ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(ResourceSpans.newBuilder()
                .setResource(Resource.newBuilder()
                    .addAttributes(attribute("service.name", "chatbot"))
                    .addAttributes(attribute("service.layer", "OTLP_GENERAL")))
                .addScopeSpans(scope))
            .build();
    }

    private static KeyValue attribute(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }
}
