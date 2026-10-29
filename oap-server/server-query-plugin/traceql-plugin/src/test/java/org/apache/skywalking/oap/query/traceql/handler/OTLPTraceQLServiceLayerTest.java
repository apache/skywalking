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

package org.apache.skywalking.oap.query.traceql.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service name values of the OTLP datasource, narrowed by a {@code service.layer} condition.
 */
public class OTLPTraceQLServiceLayerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IOTLPTraceQueryDAO dao;
    private OTLPTraceQLApiHandler handler;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock(IOTLPTraceQueryDAO.class);
        when(dao.getServices(null)).thenReturn(List.of(
            traffic("otel-demo::cart", "OTLP_GENERAL"),
            traffic("payments", "UNDEFINED"),
            traffic("mesh-svr::songs.sample-services", "OTLP_MESH")
        ));
        // The storage narrows by layer, see the storage DAOs' own tests.
        when(dao.getServices("OTLP_GENERAL")).thenReturn(List.of(traffic("otel-demo::cart", "OTLP_GENERAL")));
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder coreHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(coreServices.getService(NamingControl.class)).thenReturn(mock(NamingControl.class));
        when(coreServices.getService(TagAutoCompleteQueryService.class)).thenReturn(mock(TagAutoCompleteQueryService.class));
        final ModuleProviderHolder storageHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storageServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storageHolder);
        when(storageHolder.provider()).thenReturn(storageServices);
        when(storageServices.getService(IOTLPTraceQueryDAO.class)).thenReturn(dao);
        handler = new OTLPTraceQLApiHandler(moduleManager, new TraceQLConfig());
    }

    @Test
    public void withoutAConditionEveryServiceIsListed() throws Exception {
        assertEquals(List.of("otel-demo::cart", "payments", "mesh-svr::songs.sample-services"),
            serviceNames("resource.service.name", Optional.empty()));
    }

    @Test
    public void aLayerConditionIsAnsweredByTheCatalog() throws Exception {
        assertEquals(List.of("otel-demo::cart"),
            serviceNames("resource.service.name", Optional.of("{resource.service.layer=\"OTLP_GENERAL\"}")));
        verify(dao).getServices("OTLP_GENERAL");
        // Complete, not sampled: a sample would miss a service with no trace in the window.
        verify(dao, never()).queryTracesDebuggable(any());
    }

    @Test
    public void anUnscopedLayerIsAnsweredFromASample() throws Exception {
        // A span attribute named service.layer answers it too, which the catalog cannot see.
        serviceNames("resource.service.name", Optional.of("{.service.layer=\"OTLP_GENERAL\"}"));
        verify(dao).queryTracesDebuggable(any());
        verify(dao, never()).getServices("OTLP_GENERAL");
    }

    @Test
    public void aLayerWithAnotherConditionIsStillAnsweredFromASample() throws Exception {
        serviceNames("resource.service.name", Optional.of("{resource.service.layer=\"OTLP_GENERAL\" && status=error}"));
        verify(dao).queryTracesDebuggable(any());
    }

    private List<String> serviceNames(final String tag, final Optional<String> query) throws Exception {
        final JsonNode body = MAPPER.readTree(handler.searchTagValues(
            tag, query, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ).aggregate().join().contentUtf8());
        final List<String> values = new ArrayList<>();
        body.get("tagValues").forEach(value -> values.add(value.get("value").asText()));
        return values;
    }

    private static OTLPServiceTraffic traffic(final String serviceName, final String layer) {
        final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
        traffic.setServiceName(serviceName);
        traffic.setLayer(layer);
        return traffic;
    }
}
