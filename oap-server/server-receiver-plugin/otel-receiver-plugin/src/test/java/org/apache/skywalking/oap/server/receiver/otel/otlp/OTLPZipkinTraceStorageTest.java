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
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Zipkin storage of OTLP spans: the resource tags it stores with, the conversion, and the batch it hands to
 * the Zipkin receiver.
 */
class OTLPZipkinTraceStorageTest {
    private static final byte[] TRACE_ID = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private final List<zipkin2.Span> forwarded = new ArrayList<>();
    private final TraceSpansDropped droppedSpans = mock(TraceSpansDropped.class);
    private SpanForwardService forwardService;
    private OTLPZipkinTraceStorage storage;

    @BeforeEach
    void setUp() {
        forwardService = mock(SpanForwardService.class);
        doAnswer(inv -> forwarded.addAll(inv.getArgument(0))).when(forwardService).send(any(), any(), any());
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(SpanForwardService.class)).thenReturn(forwardService);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.find(ZipkinReceiverModule.NAME)).thenReturn(provider);
        storage = new OTLPZipkinTraceStorage(manager);
    }

    @Test
    void storesAsZipkinWithoutReportingRejections() {
        assertEquals(OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_ZIPKIN, storage.traceStorage());
        assertFalse(storage.reportsRejections());
    }

    @Test
    void aFallbackServiceNameKeyMovesIntoTheSourceTagOfACopy() {
        final Map<String, String> sent = Map.of("faas.name", "resize", "cloud.region", "eu");

        final Map<String, String> stored = storage.newExport(droppedSpans).openResource(resourceSpans(), sent);

        assertEquals(Map.of("otlp.service.name.source", "faas.name", "cloud.region", "eu"), stored);
        assertEquals(Map.of("faas.name", "resize", "cloud.region", "eu"), sent, "the tags as sent stay unchanged");
    }

    @Test
    void theServiceNameKeyStaysAndAddsNoSourceTag() {
        final Map<String, String> sent = Map.of("service.name", "cart", "faas.name", "resize");

        assertEquals(sent, storage.newExport(droppedSpans).openResource(resourceSpans(), sent));
    }

    @Test
    void aSpanIsConvertedWithTheScopeTagsAndHandedOverAtTheEnd() {
        final OTLPTraceStorage.Export export = storage.newExport(droppedSpans);
        final Map<String, String> stored = export.openResource(resourceSpans(), Map.of("service.name", "cart"));
        export.openScope(scope("io.opentelemetry.okhttp", "1.2.0"), stored);
        export.store(span(1).setKind(Span.SpanKind.SPAN_KIND_CLIENT)
                            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR))
                            .addAttributes(string("http.request.method", "GET"))
                            .build(),
                     "shop::cart", stored, Collections.emptyMap(), true);

        assertTrue(forwarded.isEmpty(), "the batch is handed over only when the export finishes");
        export.finish();

        assertEquals(1, forwarded.size());
        final zipkin2.Span converted = forwarded.get(0);
        assertEquals("0102030405060708090a0b0c0d0e0f10", converted.traceId());
        assertEquals("0000000000000001", converted.id());
        assertEquals(zipkin2.Span.Kind.CLIENT, converted.kind());
        assertEquals("shop::cart", converted.localServiceName(), "the grouped service names the local endpoint");
        assertTrue(converted.debug(), "an enforced span is a debug span, kept past the receiver's sampling");
        assertEquals("GET", converted.tags().get("http.request.method"));
        assertEquals("cart", converted.tags().get("service.name"));
        assertEquals("io.opentelemetry.okhttp", converted.tags().get("otel.library.name"));
        assertEquals("1.2.0", converted.tags().get("otel.library.version"));
        assertEquals("true", converted.tags().get("error"));
        assertEquals("STATUS_CODE_ERROR", converted.tags().get("otel.status_code"));
    }

    @Test
    void injectedTagsStayOnTheirOwnSpan() {
        final OTLPTraceStorage.Export export = storage.newExport(droppedSpans);
        final Map<String, String> stored = export.openResource(resourceSpans(), Map.of("service.name", "cart"));
        export.openScope(scope("", ""), stored);
        export.store(span(1).build(), "cart", stored, Map.of("rule.tag", "yes"), false);
        export.store(span(2).build(), "cart", stored, Collections.emptyMap(), false);
        export.finish();

        assertEquals("yes", forwarded.get(0).tags().get("rule.tag"));
        assertNull(forwarded.get(1).tags().get("rule.tag"), "the next span of the resource does not inherit it");
        assertFalse(stored.containsKey("rule.tag"), "the resource tags are not written to");
    }

    @Test
    void theReceiverCountsWhatItDropsWithTheRequestsCounter() {
        final OTLPTraceStorage.Export export = storage.newExport(droppedSpans);
        final Map<String, String> stored = export.openResource(resourceSpans(), Map.of("service.name", "cart"));
        export.openScope(scope("", ""), stored);
        export.store(span(1).build(), "cart", stored, Collections.emptyMap(), false);
        export.finish();

        verify(forwardService).send(any(), any(), same(droppedSpans));
    }

    private static ResourceSpans resourceSpans() {
        return ResourceSpans.newBuilder().setResource(Resource.getDefaultInstance()).build();
    }

    private static ScopeSpans scope(final String name, final String version) {
        return ScopeSpans.newBuilder()
                         .setScope(InstrumentationScope.newBuilder().setName(name).setVersion(version))
                         .build();
    }

    private static Span.Builder span(final int id) {
        return Span.newBuilder()
                   .setTraceId(ByteString.copyFrom(TRACE_ID))
                   .setSpanId(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) id}))
                   .setName("GET /cart")
                   .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                   .setEndTimeUnixNano(1_700_000_000_100_000_000L);
    }

    private static KeyValue string(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }
}
