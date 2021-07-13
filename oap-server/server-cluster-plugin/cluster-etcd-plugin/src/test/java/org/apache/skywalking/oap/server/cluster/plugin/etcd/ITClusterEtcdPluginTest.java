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

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@Slf4j
public class ITClusterEtcdPluginTest {
    private ClusterModuleEtcdConfig etcdConfig;

    private Client client;

    private HealthCheckMetrics healthChecker = mock(HealthCheckMetrics.class);

    private EtcdCoordinator coordinator;

    private final Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private final Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);
    private final Address internalAddress = new Address("10.0.0.3", 1002, false);

    private static final String SERVICE_NAME = "my-service";

    @ClassRule
    public static final GenericContainer CONTAINER =
        new GenericContainer(DockerImageName.parse("bitnami/etcd:3.5.0"))
            .waitingFor(Wait.forLogMessage(".*etcd setup finished!.*", 1))
            .withEnv(Collections.singletonMap("ALLOW_NONE_AUTHENTICATION", "yes"));

    @Before
    public void before() throws Exception {
        String baseUrl = "http://127.0.0.1:" + CONTAINER.getMappedPort(2379);
        etcdConfig = new ClusterModuleEtcdConfig();
        etcdConfig.setEndpoints(baseUrl);
        etcdConfig.setNamespace("skywalking/");

        etcdConfig.setServiceName(SERVICE_NAME);
        doNothing().when(healthChecker).health();

        ModuleDefineHolder manager = mock(ModuleDefineHolder.class);
        coordinator = new EtcdCoordinator(manager, etcdConfig);

        client = Whitebox.getInternalState(coordinator, "client");
        Whitebox.setInternalState(coordinator, "healthChecker", healthChecker);
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
        ByteSequence prefix = ByteSequence.from(SERVICE_NAME + "/", Charset.defaultCharset());
        GetResponse response = client.getKVClient()
                                     .get(
                                         ByteSequence.EMPTY,
                                         GetOption.newBuilder().withPrefix(prefix).build()
                                     ).get();

        response.getKvs().forEach(e -> {
            try {
                client.getKVClient().delete(e.getKey()).get();
            } catch (Exception exp) {
                log.error("", exp);
            }
        });
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
