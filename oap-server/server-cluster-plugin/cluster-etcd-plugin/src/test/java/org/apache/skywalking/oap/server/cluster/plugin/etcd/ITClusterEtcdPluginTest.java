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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import java.net.URI;
import java.util.List;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

public class ITClusterEtcdPluginTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITClusterEtcdPluginTest.class);

    private ClusterModuleEtcdConfig etcdConfig;

    private EtcdClient client;

    private HealthCheckMetrics healthChecker = mock(HealthCheckMetrics.class);

    private EtcdCoordinator coordinator;

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private Address internalAddress = new Address("10.0.0.3", 1002, false);

    private static final String SERVICE_NAME = "my-service";

    @Before
    public void before() throws Exception {
        String etcdHost = System.getProperty("etcd.host");
        String port = System.getProperty("etcd.port");
        String baseUrl = "http://" + etcdHost + ":" + port;
        LOGGER.info("etcd baseURL: {}", baseUrl);
        etcdConfig = new ClusterModuleEtcdConfig();
        etcdConfig.setServiceName(SERVICE_NAME);
        client = new EtcdClient(URI.create(baseUrl));
        doNothing().when(healthChecker).health();
        ModuleDefineHolder manager = mock(ModuleDefineHolder.class);
        coordinator = new EtcdCoordinator(manager, etcdConfig, client);
        Whitebox.setInternalState(coordinator, "healthChecker", healthChecker);
    }

    @After
    public void after() throws Exception {
        client.close();
    }

    @Test
    public void registerRemote() throws Throwable {
        registerRemote(remoteAddress);
        clear();
    }

    @Test
    public void registerSelfRemote() throws Throwable {
        registerRemote(selfRemoteAddress);
        clear();
    }

    @Test
    public void registerRemoteUsingInternal() throws Throwable {
        etcdConfig.setInternalComHost(internalAddress.getHost());
        etcdConfig.setInternalComPort(internalAddress.getPort());
        etcdConfig.setServiceName(SERVICE_NAME);
        registerRemote(internalAddress);
        clear();
    }

    @Test
    public void queryRemoteNodes() throws Throwable {
        registerRemote(selfRemoteAddress);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertEquals(1, remoteInstances.size());

        RemoteInstance selfInstance = remoteInstances.get(0);
        velidate(selfRemoteAddress, selfInstance);
        clear();
    }

    private void velidate(Address originArress, RemoteInstance instance) {
        Address instanceAddress = instance.getAddress();
        assertEquals(originArress.getHost(), instanceAddress.getHost());
        assertEquals(originArress.getPort(), instanceAddress.getPort());
    }

    private void registerRemote(Address address) throws Throwable {
        coordinator.registerRemote(new RemoteInstance(address));
        EtcdEndpoint endpoint = afterRegister();
        verifyRegistration(address, endpoint);
    }

    private EtcdEndpoint afterRegister() throws Throwable {
        List<RemoteInstance> list = coordinator.queryRemoteNodes();
        assertEquals(list.size(), 1L);
        return buildEndpoint(list.get(0));
    }

    private void clear() throws Throwable {
        EtcdKeysResponse response = client.get(SERVICE_NAME + "/").send().get();
        List<EtcdKeysResponse.EtcdNode> nodes = response.getNode().getNodes();

        for (EtcdKeysResponse.EtcdNode node : nodes) {
            client.delete(node.getKey()).send().get();
        }
    }

    private void verifyRegistration(Address remoteAddress, EtcdEndpoint endpoint) {
        assertNotNull(endpoint);
        assertEquals(SERVICE_NAME, endpoint.getServiceName());
        assertEquals(remoteAddress.getHost(), endpoint.getHost());
        assertEquals(remoteAddress.getPort(), endpoint.getPort());
    }

    private EtcdEndpoint buildEndpoint(RemoteInstance instance) {
        Address address = instance.getAddress();
        EtcdEndpoint endpoint = new EtcdEndpoint.Builder().host(address.getHost())
                                                          .port(address.getPort())
                                                          .serviceName(SERVICE_NAME)
                                                          .build();
        return endpoint;
    }

}
