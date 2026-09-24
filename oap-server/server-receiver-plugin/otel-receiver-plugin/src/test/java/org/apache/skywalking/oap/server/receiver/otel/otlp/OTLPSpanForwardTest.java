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
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPService;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPServiceRelation;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPServiceSpan;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPSpan;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TagAutocomplete;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OTLPSpanForwardTest {
    private static final byte[] TRACE_ID = {
        0x0a, (byte) 0xf7, 0x65, 0x19, 0x16, (byte) 0xcd, 0x43, (byte) 0xdd,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
    };
    private static final byte[] SPAN_ID = {0x00, (byte) 0xb7, (byte) 0xad, 0x6b, 0x71, 0x69, 0x20, 0x33};
    private static final byte[] PARENT_SPAN_ID = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    private static final long START_NANOS = 1_700_000_000_123_456_789L;

    private ModuleManager moduleManager;
    private SourceReceiver receiver;
    private OtelMetricReceiverConfig config;

    @BeforeEach
    void setUp() {
        moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        final NamingControl namingControl = mock(NamingControl.class);
        receiver = mock(SourceReceiver.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(NamingControl.class)).thenReturn(namingControl);
        when(serviceHolder.getService(SourceReceiver.class)).thenReturn(receiver);
        when(namingControl.formatServiceName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(namingControl.formatEndpointName(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        config = new OtelMetricReceiverConfig();
        config.setOtlpTraceSearchableTags("span.http.request.method,span.peer.service");
    }

    @Test
    void shouldIndexColumnsCatalogsAndKeepTheSpanVerbatim() throws Exception {
        final OTLPSpanForward forward = new OTLPSpanForward(config, moduleManager);
        final Resource resource = Resource.newBuilder()
                                          .addAttributes(string("service.name", "checkout"))
                                          .addAttributes(string("service.instance.id", "checkout-1"))
                                          .build();
        final InstrumentationScope scope = InstrumentationScope.newBuilder().setName("io.opentelemetry.http").build();
        final String tooLong = "x".repeat(Tag.TAG_LENGTH + 1);
        final Span span = span().addAttributes(string("http.request.method", "GET"))
                                .addAttributes(string("peer.service", "payment"))
                                .addAttributes(KeyValue.newBuilder()
                                                       .setKey("http.response.status_code")
                                                       .setValue(AnyValue.newBuilder().setIntValue(200)))
                                .addAttributes(string("db.statement", tooLong))
                                .build();

        assertTrue(forward.send(resource, "https://opentelemetry.io/schemas/1.26.0", scope, "", span, "checkout",
                                Map.of("service.name", "checkout", "service.instance.id", "checkout-1"),
                                Map.of("gen_ai.injected", "yes")
        ));

        final List<ISource> sources = received();
        final OTLPSpan stored = single(sources, OTLPSpan.class);
        assertEquals("0af7651916cd43dd0000000000000001", stored.getTraceId());
        assertEquals("00b7ad6b71692033", stored.getSpanId());
        assertEquals("0102030405060708", stored.getParentSpanId());
        assertEquals("checkout", stored.getServiceName());
        assertEquals("checkout-1", stored.getServiceInstance());
        assertEquals("io.opentelemetry.http", stored.getScopeName());
        assertEquals("GET /checkout", stored.getName());
        assertEquals(Span.SpanKind.SPAN_KIND_SERVER.getNumber(), stored.getKind());
        assertEquals(Status.StatusCode.STATUS_CODE_ERROR.getNumber(), stored.getStatusCode());
        assertEquals("payment", stored.getPeerService());
        assertEquals(START_NANOS / 1_000_000L, stored.getStartTime());
        assertEquals(2_000_000L, stored.getDuration());
        // resource attributes under `resource.`, span attributes under `span.`, so the scopes stay apart
        assertTrue(stored.getTags().containsAll(Arrays.asList(
            "resource.service.name=checkout", "resource.service.instance.id=checkout-1", "span.http.request.method=GET",
            "span.peer.service=payment", "span.http.response.status_code=200", "span.gen_ai.injected=yes"
        )));
        assertFalse(stored.getTags().stream().anyMatch(tag -> tag.contains("db.statement=")));
        assertFalse(stored.getTags().stream().anyMatch(tag -> tag.startsWith("service.name=")));

        final ResourceSpans dataBinary = ResourceSpans.parseFrom(stored.getDataBinary());
        assertEquals(resource, dataBinary.getResource());
        assertEquals("https://opentelemetry.io/schemas/1.26.0", dataBinary.getSchemaUrl());
        assertEquals(scope, dataBinary.getScopeSpans(0).getScope());
        final Span storedSpan = dataBinary.getScopeSpans(0).getSpans(0);
        // The injected attribute is the only difference from the wire span.
        assertEquals(span, storedSpan.toBuilder().removeAttributes(storedSpan.getAttributesCount() - 1).build());
        assertEquals("gen_ai.injected", storedSpan.getAttributes(storedSpan.getAttributesCount() - 1).getKey());

        assertEquals("checkout", single(sources, OTLPService.class).getServiceName());
        final OTLPServiceSpan serviceSpan = single(sources, OTLPServiceSpan.class);
        assertEquals("checkout", serviceSpan.getServiceName());
        assertEquals("GET /checkout", serviceSpan.getSpanName());
        final OTLPServiceRelation relation = single(sources, OTLPServiceRelation.class);
        assertEquals("checkout", relation.getServiceName());
        assertEquals("payment", relation.getPeerService());

        final Map<String, String> autocomplete = sources.stream()
                                                        .filter(TagAutocomplete.class::isInstance)
                                                        .map(TagAutocomplete.class::cast)
                                                        .peek(tag -> assertEquals(TagType.OTLP, tag.getTagType()))
                                                        .collect(Collectors.toMap(
                                                            TagAutocomplete::getTagKey, TagAutocomplete::getTagValue));
        assertEquals(Map.of(
            "span.http.request.method", "GET",
            "span.peer.service", "payment",
            OTLPSpanForward.SCOPE_NAME_TAG, "io.opentelemetry.http",
            OTLPSpanForward.INSTANCE_TAG, "checkout-1"
        ), autocomplete);
    }

    @Test
    void shouldPublishAutocompleteKeysUnderTheScopeTheyWereSeenIn() {
        config.setOtlpTraceSearchableTags(
            "resource.deployment.environment,span.deployment.environment,span.http.request.method");
        final OTLPSpanForward forward = new OTLPSpanForward(config, moduleManager);
        final Resource resource = Resource.newBuilder()
                                          .addAttributes(string("service.name", "checkout"))
                                          .addAttributes(string("deployment.environment", "prod"))
                                          .build();
        final Span span = span().addAttributes(string("http.request.method", "GET"))
                                .addAttributes(string("deployment.environment", "canary"))
                                .build();

        assertTrue(forward.send(resource, "", InstrumentationScope.getDefaultInstance(), "", span, "checkout",
                                Map.of("service.name", "checkout"), Map.of()));

        // Each configured entry names one scope; a key listed for both is published once per scope.
        final Set<String> autocomplete = received().stream()
                                                   .filter(TagAutocomplete.class::isInstance)
                                                   .map(TagAutocomplete.class::cast)
                                                   .map(tag -> tag.getTagKey() + "=" + tag.getTagValue())
                                                   .collect(Collectors.toSet());
        assertEquals(Set.of(
            "resource.deployment.environment=prod",
            "span.deployment.environment=canary",
            "span.http.request.method=GET"
        ), autocomplete);
    }

    @Test
    void shouldPublishOnlyTheScopeAnEntryNames() {
        config.setOtlpTraceSearchableTags("resource.deployment.environment");
        final OTLPSpanForward forward = new OTLPSpanForward(config, moduleManager);
        final Resource resource = Resource.newBuilder().addAttributes(string("deployment.environment", "prod")).build();
        final Span span = span().addAttributes(string("deployment.environment", "canary")).build();

        assertTrue(forward.send(resource, "", InstrumentationScope.getDefaultInstance(), "", span, "checkout",
                                Map.of(), Map.of()));

        assertEquals(Set.of("resource.deployment.environment=prod"), received().stream()
                                                                             .filter(TagAutocomplete.class::isInstance)
                                                                             .map(TagAutocomplete.class::cast)
                                                                             .map(tag -> tag.getTagKey() + "=" + tag.getTagValue())
                                                                             .collect(Collectors.toSet()));
    }

    @Test
    void shouldDropSpansOverTheRateLimit() {
        config.setOtlpTraceMaxSpansPerSecond(1);
        final OTLPSpanForward forward = new OTLPSpanForward(config, moduleManager);

        assertTrue(send(forward, TRACE_ID));
        assertFalse(send(forward, TRACE_ID));
    }

    @Test
    void shouldSampleOnTheLowBitsOfTheTraceId() {
        config.setOtlpTraceSampleRate(5000);
        final OTLPSpanForward forward = new OTLPSpanForward(config, moduleManager);
        final byte[] highTraceId = TRACE_ID.clone();
        highTraceId[8] = 0x7f;
        highTraceId[9] = (byte) 0xff;

        assertTrue(send(forward, TRACE_ID));
        assertFalse(send(forward, highTraceId));
    }

    private boolean send(final OTLPSpanForward forward, final byte[] traceId) {
        return forward.send(
            Resource.getDefaultInstance(), "", InstrumentationScope.getDefaultInstance(), "",
            span().setTraceId(ByteString.copyFrom(traceId)).build(), "checkout", Map.of(), Map.of()
        );
    }

    private static Span.Builder span() {
        return Span.newBuilder()
                   .setTraceId(ByteString.copyFrom(TRACE_ID))
                   .setSpanId(ByteString.copyFrom(SPAN_ID))
                   .setParentSpanId(ByteString.copyFrom(PARENT_SPAN_ID))
                   .setName("GET /checkout")
                   .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                   .setStartTimeUnixNano(START_NANOS)
                   .setEndTimeUnixNano(START_NANOS + 2_000_000L)
                   .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR));
    }

    private static KeyValue string(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    private List<ISource> received() {
        final ArgumentCaptor<ISource> captor = ArgumentCaptor.forClass(ISource.class);
        verify(receiver, atLeastOnce()).receive(captor.capture());
        return captor.getAllValues();
    }

    private static <T> T single(final List<ISource> sources, final Class<T> type) {
        final List<T> matches = sources.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
        assertEquals(1, matches.size(), "expected exactly one " + type.getSimpleName());
        return matches.get(0);
    }
}
