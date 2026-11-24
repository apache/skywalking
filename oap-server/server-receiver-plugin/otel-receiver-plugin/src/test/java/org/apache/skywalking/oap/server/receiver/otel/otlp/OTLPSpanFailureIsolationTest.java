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

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A span whose processing fails is dropped alone and counted as {@code process_failed}; the spans around it are
 * stored, and every span is timed.
 */
class OTLPSpanFailureIsolationTest {

    @Test
    void aPhaseOneListenerFailureCostsOnlyItsSpan() throws Exception {
        final SpanListenerManager listeners = mock(SpanListenerManager.class);
        final AtomicInteger calls = new AtomicInteger();
        when(listeners.notifyOTLPPhase(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("broken listener");
            }
            return SpanListenerResult.CONTINUE;
        });
        when(listeners.notifyNativeOTLPPhase(any(), any(), any(), any(), any()))
            .thenReturn(SpanListenerResult.CONTINUE);
        final OpenTelemetryTraceHandler handler = handler(listeners);
        final OTLPSpanForward forward = mock(OTLPSpanForward.class);
        when(forward.send(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenReturn(OTLPSpanForward.Outcome.STORED);
        replaceForward(handler, forward);
        final HistogramMetrics latency = mock(HistogramMetrics.class);
        final TraceSpansDropped dropped = mock(TraceSpansDropped.class);

        handler.processExport(request("before", "bad", "after"), latency, dropped);

        final ArgumentCaptor<Span> stored = ArgumentCaptor.forClass(Span.class);
        verify(forward, times(2)).send(any(), any(), any(), any(), stored.capture(), any(), any(), any(),
                                       anyBoolean());
        assertEquals(List.of("before", "after"),
                     stored.getAllValues().stream().map(Span::getName).collect(Collectors.toList()));
        verify(dropped).count(TraceSpansDropped.Reason.PROCESS_FAILED, 1);
        verify(dropped, never()).count(eq(TraceSpansDropped.Reason.STORE_FAILED), anyLong());
        verify(latency, times(3)).observe(anyDouble());
    }

    /**
     * The handler with native storage, its TAL analyzer persisting every span.
     */
    private static OpenTelemetryTraceHandler handler(final SpanListenerManager listeners) {
        final ITraceAnalyzerService analyzer = mock(ITraceAnalyzerService.class);
        when(analyzer.doAnalysis(any(), any(), any(), any()))
            .thenReturn(SpanAnalysisResult.builder().persist(true).build());
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.has(TraceAnalyzerModule.NAME)).thenReturn(true);
        provide(manager, TraceAnalyzerModule.NAME, ITraceAnalyzerService.class, analyzer);
        provide(manager, CoreModule.NAME, SpanListenerManager.class, listeners);
        provide(manager, TelemetryModule.NAME, MetricsCreator.class, mock(MetricsCreator.class));

        final OpenTelemetryTraceHandler handler = new OpenTelemetryTraceHandler();
        handler.init(manager, new OtelMetricReceiverConfig());
        return handler;
    }

    private static void replaceForward(final OpenTelemetryTraceHandler handler,
                                       final OTLPSpanForward forward) throws Exception {
        final Field storageField = OpenTelemetryTraceHandler.class.getDeclaredField("storage");
        storageField.setAccessible(true);
        final Field forwardField = OTLPNativeTraceStorage.class.getDeclaredField("otlpSpanForward");
        forwardField.setAccessible(true);
        forwardField.set(storageField.get(handler), forward);
    }

    private static <T extends Service> void provide(final ModuleManager manager, final String module,
                                                    final Class<T> type, final T service) {
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(type)).thenReturn(service);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        when(manager.find(module)).thenReturn(provider);
    }

    /**
     * One resource of service {@code cart} with one scope holding a span per name, in order.
     */
    private static ExportTraceServiceRequest request(final String... spanNames) {
        final ScopeSpans.Builder scope = ScopeSpans.newBuilder();
        for (int i = 0; i < spanNames.length; i++) {
            scope.addSpans(Span.newBuilder()
                               .setTraceId(ByteString.copyFrom(new byte[16]))
                               .setSpanId(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) (i + 1)}))
                               .setName(spanNames[i])
                               .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                               .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                               .setEndTimeUnixNano(1_700_000_000_100_000_000L));
        }
        return ExportTraceServiceRequest.newBuilder()
                                        .addResourceSpans(ResourceSpans.newBuilder()
                                                                       .setResource(Resource.newBuilder().addAttributes(
                                                                           KeyValue.newBuilder()
                                                                                   .setKey("service.name")
                                                                                   .setValue(AnyValue.newBuilder()
                                                                                                     .setStringValue("cart"))))
                                                                       .addScopeSpans(scope))
                                        .build();
    }
}
