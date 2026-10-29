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
import io.opentelemetry.proto.trace.v1.Span;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPService;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OTLPSpanForwardCatalogLayerTest {
    @Test
    public void aRegisteredLayerIsFiledAsWritten() {
        assertEquals("OTLP_GENERAL", OTLPSpanForward.catalogLayerOf(Map.of("service.layer", "OTLP_GENERAL")));
        assertEquals("OTLP_MESH", OTLPSpanForward.catalogLayerOf(Map.of("service.layer", "OTLP_MESH")));
    }

    @Test
    public void anythingElseIsUndefined() {
        // Matched exactly, as the trace analyzer matches it: a client cannot add layers to the catalog.
        for (final String layer : new String[] {"", " ", "otlp_general", "OTLP_GENRAL"}) {
            assertEquals("UNDEFINED", OTLPSpanForward.catalogLayerOf(Map.of("service.layer", layer)), layer);
        }
        assertEquals("UNDEFINED", OTLPSpanForward.catalogLayerOf(Map.of()));
    }

    @Test
    public void theStoredResourceCarriesTheCatalogLayer() {
        final Resource typo = resource("service.name", "app", "service.layer", "otlp_general");
        assertEquals(List.of("service.name=app", "service.layer=UNDEFINED"),
            attributes(OTLPSpanForward.withCatalogLayer(typo, "UNDEFINED")), "replaced in place");

        assertEquals(List.of("service.name=app", "service.layer=UNDEFINED"),
            attributes(OTLPSpanForward.withCatalogLayer(resource("service.name", "app"), "UNDEFINED")),
            "added when absent");

        final Resource general = resource("service.name", "app", "service.layer", "OTLP_GENERAL");
        assertSame(general, OTLPSpanForward.withCatalogLayer(general, "OTLP_GENERAL"), "untouched when it agrees");

        final Resource repeated = resource("service.layer", "FOO", "service.name", "app", "service.layer", "BAR");
        assertEquals(List.of("service.layer=UNDEFINED", "service.name=app"),
            attributes(OTLPSpanForward.withCatalogLayer(repeated, "UNDEFINED")), "one layer, where the first was");

        final Resource agreeingTwice = resource("service.layer", "OTLP_GENERAL", "service.layer", "OTLP_MESH");
        assertEquals(List.of("service.layer=OTLP_GENERAL"),
            attributes(OTLPSpanForward.withCatalogLayer(agreeingTwice, "OTLP_GENERAL")), "a repeat is dropped");
    }

    @Test
    public void theCatalogRowNamesTheLayerTheStoredSpanCarries() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        final NamingControl namingControl = mock(NamingControl.class);
        final SourceReceiver receiver = mock(SourceReceiver.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(NamingControl.class)).thenReturn(namingControl);
        when(serviceHolder.getService(SourceReceiver.class)).thenReturn(receiver);
        when(namingControl.formatServiceName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(namingControl.formatEndpointName(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        final OTLPSpanForward forward = new OTLPSpanForward(new OtelMetricReceiverConfig(), moduleManager);
        final Span span = Span.newBuilder()
                              .setTraceId(ByteString.copyFrom(new byte[16]))
                              .setSpanId(ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
                              .setName("GET /")
                              .setStartTimeUnixNano(1_700_000_000_000_000_000L)
                              .setEndTimeUnixNano(1_700_000_000_100_000_000L)
                              .build();

        // The tags as received named a typo; the resource to store carries the catalog's layer.
        forward.send(resource("service.name", "app", "service.layer", "UNDEFINED"), "",
                     InstrumentationScope.getDefaultInstance(), "", span, "app",
                     Map.of("service.name", "app", "service.layer", "otlp_general"), Map.of(), true);

        final ArgumentCaptor<ISource> captor = ArgumentCaptor.forClass(ISource.class);
        verify(receiver, atLeastOnce()).receive(captor.capture());
        final OTLPService service = captor.getAllValues().stream().filter(OTLPService.class::isInstance)
                                          .map(OTLPService.class::cast).findFirst().orElseThrow();
        assertEquals("UNDEFINED", service.getLayer());
    }

    private static Resource resource(final String... keyValues) {
        final Resource.Builder resource = Resource.newBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            resource.addAttributes(KeyValue.newBuilder()
                                           .setKey(keyValues[i])
                                           .setValue(AnyValue.newBuilder().setStringValue(keyValues[i + 1])));
        }
        return resource.build();
    }

    private static List<String> attributes(final Resource resource) {
        return resource.getAttributesList().stream()
                       .map(kv -> kv.getKey() + "=" + kv.getValue().getStringValue())
                       .collect(Collectors.toList());
    }
}
