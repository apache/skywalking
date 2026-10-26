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

package org.apache.skywalking.oap.server.core.query;

import java.util.List;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OTLPTraceQueryServiceTest {
    private IOTLPTraceQueryDAO dao;
    private OTLPTraceQueryService service;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock(IOTLPTraceQueryDAO.class);
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IOTLPTraceQueryDAO.class)).thenReturn(dao);
        service = new OTLPTraceQueryService(moduleManager);
    }

    @Test
    public void aServiceStoredUnderTwoLayersIsListedOnce() throws Exception {
        // An app that opted in after it was first seen has a row without its layer too.
        when(dao.getServices(null)).thenReturn(List.of(
            traffic("otel-demo::cart", "UNDEFINED"),
            traffic("otel-demo::cart", "OTLP_GENERAL"),
            traffic("mesh-svr::songs.sample-services", "OTLP_MESH")
        ));

        assertEquals(List.of("otel-demo::cart", "mesh-svr::songs.sample-services"), service.getServiceNames(null));
    }

    @Test
    public void theLayerIsHandedToTheStorage() throws Exception {
        when(dao.getServices("OTLP_GENERAL")).thenReturn(List.of(
            traffic("otel-demo::cart", "OTLP_GENERAL"),
            traffic("otel-demo::frontend", "OTLP_GENERAL")
        ));

        assertEquals(List.of("otel-demo::cart", "otel-demo::frontend"), service.getServiceNames("OTLP_GENERAL"));
    }

    private static OTLPServiceTraffic traffic(final String serviceName, final String layer) {
        final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
        traffic.setServiceName(serviceName);
        traffic.setLayer(layer);
        return traffic;
    }
}
