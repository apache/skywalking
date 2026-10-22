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

import java.lang.reflect.Field;
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
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
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
    public void testServiceTopologyBuild() throws Exception {
        Service svrA = getSvrA();
        Service svrB = getSvrB();
        final ServiceTopologyBuilder serviceTopologyBuilder = new ServiceTopologyBuilder(moduleManager);
        Field metadataQueryServiceField = ServiceTopologyBuilder.class.getDeclaredField("metadataQueryService");
        metadataQueryServiceField.setAccessible(true);
        metadataQueryServiceField.set(serviceTopologyBuilder, metadataQueryService);
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

    @SneakyThrows
    @Test
    public void testRegisteredDestNamedLikeAnAliasAddressKeepsItsNode() {
        // An agent reached `agent::frontend` at the bare host `frontend`; an OTLP service is also named `frontend`.
        final String agentFrontend = IDManager.ServiceID.buildId("agent::frontend", true);
        final ServiceTopologyBuilder builder = builderWithAlias("frontend", agentFrontend);
        final String proxy = IDManager.ServiceID.buildId("frontend-proxy", true);
        final String frontend = IDManager.ServiceID.buildId("frontend", true);
        when(metadataQueryService.getService(frontend)).thenReturn(registeredService(frontend, "frontend"));

        final Topology topology = builder.build(List.of(clientCall(proxy, frontend)), new ArrayList<>());

        Assertions.assertEquals(1, topology.getCalls().size());
        Assertions.assertEquals(frontend, topology.getCalls().get(0).getTarget());
        Assertions.assertTrue(topology.getNodes().stream().noneMatch(n -> n.getId().equals(agentFrontend)));
    }

    @SneakyThrows
    @Test
    public void testUnregisteredAddressMergesIntoTheServiceItsAliasNames() {
        // A call recorded before the address's alias existed names the address itself. An unregistered
        // address-named destination folds through its alias, whether its id is real or conjectured.
        final String songs = IDManager.ServiceID.buildId("songs", true);
        final ServiceTopologyBuilder builder = builderWithAlias("10.0.0.7:8080", songs);
        final String gateway = IDManager.ServiceID.buildId("gateway", true);
        for (final boolean real : new boolean[] {false, true}) {
            final String address = IDManager.ServiceID.buildId("10.0.0.7:8080", real);

            final Topology topology = builder.build(List.of(clientCall(gateway, address)), new ArrayList<>());

            Assertions.assertEquals(1, topology.getCalls().size());
            Assertions.assertEquals(songs, topology.getCalls().get(0).getTarget());
            Assertions.assertTrue(topology.getNodes().stream().noneMatch(n -> n.getId().equals(address)));
        }
    }

    @SneakyThrows
    @Test
    public void testSelfCallDialedByTheCallersOwnNameMergesIntoTheServiceItsAliasNames() {
        // nginx, service `User_Service_Name`, names its upstream by its own service name; the downstream registered
        // that name as its alias, but the first calls were recorded before the alias landed.
        final String nginx = IDManager.ServiceID.buildId("User_Service_Name", true);
        final String end = IDManager.ServiceID.buildId("end-provider", true);
        final ServiceTopologyBuilder builder = builderWithAlias("User_Service_Name", end);
        when(metadataQueryService.getService(nginx)).thenReturn(registeredService(nginx, "User_Service_Name"));

        final Topology topology = builder.build(
            List.of(clientCall(nginx, nginx), clientCall(nginx, end)), new ArrayList<>());

        Assertions.assertEquals(1, topology.getCalls().size(), "the self-call is the call to the downstream");
        Assertions.assertEquals(nginx, topology.getCalls().get(0).getSource());
        Assertions.assertEquals(end, topology.getCalls().get(0).getTarget());
    }

    @SneakyThrows
    @Test
    public void testSelfCallWhoseAliasNamesItselfKeepsItsLoop() {
        // `cart` calls its own host `cart`, which its own server spans registered.
        final String cart = IDManager.ServiceID.buildId("cart", true);
        final ServiceTopologyBuilder builder = builderWithAlias("cart", cart);
        when(metadataQueryService.getService(cart)).thenReturn(registeredService(cart, "cart"));

        final Topology topology = builder.build(List.of(clientCall(cart, cart)), new ArrayList<>());

        Assertions.assertEquals(1, topology.getCalls().size());
        Assertions.assertEquals(cart, topology.getCalls().get(0).getSource());
        Assertions.assertEquals(cart, topology.getCalls().get(0).getTarget());
    }

    private static Service registeredService(final String id, final String name) {
        final Service service = new Service();
        service.setId(id);
        service.setName(name);
        service.getLayers().add(Layer.GENERAL.name());
        return service;
    }

    private ServiceTopologyBuilder builderWithAlias(final String address,
                                                    final String representServiceId) throws Exception {
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        alias.setAddress(address);
        alias.setRepresentServiceId(representServiceId);
        final NetworkAddressAliasCache aliasCache = Mockito.mock(NetworkAddressAliasCache.class);
        when(aliasCache.get(address)).thenReturn(alias);
        final ModuleManager manager = new MockModuleManager() {
            @Override
            protected void init() {
                register(CoreModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(
                            IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());
                        registerServiceImplementation(NetworkAddressAliasCache.class, aliasCache);
                    }
                });
            }
        };
        final ServiceTopologyBuilder builder = new ServiceTopologyBuilder(manager);
        final Field metadataQueryServiceField = ServiceTopologyBuilder.class.getDeclaredField("metadataQueryService");
        metadataQueryServiceField.setAccessible(true);
        metadataQueryServiceField.set(builder, metadataQueryService);
        return builder;
    }

    private static Call.CallDetail clientCall(final String sourceServiceId, final String destServiceId) {
        final Call.CallDetail call = new Call.CallDetail();
        call.buildFromServiceRelation(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(sourceServiceId, destServiceId)),
            // http
            49,
            DetectPoint.CLIENT
        );
        return call;
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
