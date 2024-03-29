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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.MockModuleManager;
import org.apache.skywalking.oap.server.core.MockModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.ComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.Node;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.Topology;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.when;

public class ServiceTopologyBuilderTest {
    private CoreModuleProvider moduleProvider;
    private ModuleManager moduleManager;
    private MetadataQueryService metadataQueryService;

    @BeforeEach
    public void setupMetrics() throws Throwable {
        moduleProvider = Mockito.mock(CoreModuleProvider.class);
        metadataQueryService = Mockito.mock(MetadataQueryService.class);
        moduleManager = new MockModuleManager() {
            @Override
            protected void init() {
                register(CoreModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(
                            IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());
                        registerServiceImplementation(
                            NetworkAddressAliasCache.class, new NetworkAddressAliasCache(new CoreModuleConfig()));
                    }
                });
            }
        };
    }

    @SneakyThrows
    @Test
    public void testServiceTopologyBuild() {
        Service svrA = getSvrA();
        Service svrB = getSvrB();
        final ServiceTopologyBuilder serviceTopologyBuilder = new ServiceTopologyBuilder(moduleManager);
        Whitebox.setInternalState(serviceTopologyBuilder, "metadataQueryService", metadataQueryService);
        when(metadataQueryService.getService(svrA.getId())).thenReturn(svrA);
        when(metadataQueryService.getService(svrB.getId())).thenReturn(svrB);
        List<Call.CallDetail> serviceRelationClientCalls = new ArrayList<>();
        Call.CallDetail call1 = new Call.CallDetail();
        call1.buildFromServiceRelation(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(
                    IDManager.ServiceID.buildId(svrA.getName(), true),
                    IDManager.ServiceID.buildId(svrB.getName(), true)
                )
            ),
            // mtls
            142,
            DetectPoint.CLIENT
        );
        serviceRelationClientCalls.add(call1);
        Call.CallDetail call2 = new Call.CallDetail();
        call2.buildFromServiceRelation(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(
                    IDManager.ServiceID.buildId(svrA.getName(), true),
                    IDManager.ServiceID.buildId(svrB.getName(), true)
                )
            ),
            // http
            49,
            DetectPoint.CLIENT
        );
        serviceRelationClientCalls.add(call2);

        List<Call.CallDetail> serviceRelationServerCalls = new ArrayList<>();
        Call.CallDetail call3 = new Call.CallDetail();
        call3.buildFromServiceRelation(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(
                    IDManager.ServiceID.buildId(svrA.getName(), true),
                    IDManager.ServiceID.buildId(svrB.getName(), true)
                )
            ),
            // mtls
            142,
            DetectPoint.SERVER
        );
        serviceRelationServerCalls.add(call3);
        Call.CallDetail call4 = new Call.CallDetail();
        call4.buildFromServiceRelation(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(
                    IDManager.ServiceID.buildId(svrA.getName(), true),
                    IDManager.ServiceID.buildId(svrB.getName(), true)
                )
            ),
            // http
            49,
            DetectPoint.SERVER
        );
        serviceRelationServerCalls.add(call4);
        final Topology topology = serviceTopologyBuilder.build(serviceRelationClientCalls, serviceRelationServerCalls);
        Assertions.assertEquals(2, topology.getNodes().size());
        for (final Node node : topology.getNodes()) {
            if (node.getName().equals("SvrB")) {
                Assertions.assertEquals("http", node.getType());
                Assertions.assertEquals(Set.of(Layer.MESH.name(), Layer.MESH_DP.name()), node.getLayers());
            } else if (node.getName().equals("SvrA")) {
                Assertions.assertEquals(null, node.getType());
                Assertions.assertEquals(Set.of(Layer.GENERAL.name()), node.getLayers());
            }
        }
        for (final Call call : topology.getCalls()) {
            Assertions.assertEquals(2, call.getSourceComponents().size());
            Assertions.assertEquals(List.of("mtls", "http"), call.getTargetComponents());
        }
    }

    private Service getSvrA() {
        Service service = new Service();
        service.setId(IDManager.ServiceID.buildId("SvrA", true));
        service.setName("SvrA");
        service.setShortName("SvrA");
        service.setLayers(Set.of(Layer.GENERAL.name()));
        return service;
    }

    private Service getSvrB() {
        Service service = new Service();
        service.setId(IDManager.ServiceID.buildId("SvrB", true));
        service.setName("SvrB");
        service.setShortName("SvrB");
        service.setLayers(Set.of(Layer.MESH.name(), Layer.MESH_DP.name()));
        return service;
    }
}
