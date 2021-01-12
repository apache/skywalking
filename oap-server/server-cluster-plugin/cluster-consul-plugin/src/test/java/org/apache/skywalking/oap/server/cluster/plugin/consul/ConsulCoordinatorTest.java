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

package org.apache.skywalking.oap.server.cluster.plugin.consul;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConsulCoordinatorTest {

    private Consul consul = mock(Consul.class);

    private ClusterModuleConsulConfig consulConfig = new ClusterModuleConsulConfig();

    private ConsulCoordinator coordinator;
    private HealthCheckMetrics healthChecker = mock(HealthCheckMetrics.class);
    private ConsulResponse<List<ServiceHealth>> consulResponse;

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private Address internalAddress = new Address("10.0.0.3", 1002, false);

    private AgentClient agentClient = mock(AgentClient.class);

    private static final String SERVICE_NAME = "my-service";

    @Before
    public void setUp() {
        consulConfig.setServiceName(SERVICE_NAME);
        ModuleDefineHolder manager = mock(ModuleDefineHolder.class);
        coordinator = new ConsulCoordinator(manager, consulConfig, consul);
        Whitebox.setInternalState(coordinator, "healthChecker", healthChecker);
        consulResponse = mock(ConsulResponse.class);

        HealthClient healthClient = mock(HealthClient.class);
        when(healthClient.getHealthyServiceInstances(anyString())).thenReturn(consulResponse);

        when(consul.healthClient()).thenReturn(healthClient);
        when(consul.agentClient()).thenReturn(agentClient);

        doNothing().when(healthChecker).health();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queryRemoteNodesWithNonOrEmpty() {
        when(consulResponse.getResponse()).thenReturn(null, Collections.emptyList());
        assertEquals(0, coordinator.queryRemoteNodes().size());
        assertEquals(0, coordinator.queryRemoteNodes().size());
    }

    @Test
    public void queryRemoteNodes() {
        registerSelfRemote();
        List<ServiceHealth> serviceHealths = mockHealth();
        when(consulResponse.getResponse()).thenReturn(serviceHealths);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertEquals(2, remoteInstances.size());

        RemoteInstance selfInstance = remoteInstances.get(0);
        velidate(selfRemoteAddress, selfInstance);

        RemoteInstance notSelfInstance = remoteInstances.get(1);
        velidate(remoteAddress, notSelfInstance);
    }

    @Test
    public void queryRemoteNodesWithNullSelf() {
        List<ServiceHealth> serviceHealths = mockHealth();
        when(consulResponse.getResponse()).thenReturn(serviceHealths);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        // filter empty address
        assertEquals(2, remoteInstances.size());
    }

    @Test
    public void registerRemote() {
        registerRemote(remoteAddress);
    }

    @Test
    public void registerSelfRemote() {
        registerRemote(selfRemoteAddress);
    }

    @Test
    public void registerRemoteUsingInternal() {
        consulConfig.setInternalComHost(internalAddress.getHost());
        consulConfig.setInternalComPort(internalAddress.getPort());
        registerRemote(internalAddress);
    }

    private void velidate(Address originArress, RemoteInstance instance) {
        Address instanceAddress = instance.getAddress();
        assertEquals(originArress.getHost(), instanceAddress.getHost());
        assertEquals(originArress.getPort(), instanceAddress.getPort());
    }

    private void registerRemote(Address address) {
        coordinator.registerRemote(new RemoteInstance(address));
        Registration registration = afterRegister();
        verifyRegistration(address, registration);
    }

    private Registration afterRegister() {
        ArgumentCaptor<Registration> argumentCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(agentClient).register(argumentCaptor.capture());
        return argumentCaptor.getValue();
    }

    private void verifyRegistration(Address remoteAddress, Registration registration) {
        assertNotNull(registration);
        assertEquals(SERVICE_NAME, registration.getName());
        assertEquals(remoteAddress.getHost() + "_" + remoteAddress.getPort(), registration.getId());
        assertTrue(registration.getAddress().isPresent());
        assertEquals(remoteAddress.getHost(), registration.getAddress().get());
        assertTrue(registration.getPort().isPresent());
        assertEquals(remoteAddress.getPort(), registration.getPort().get().intValue());
        assertTrue(registration.getCheck().isPresent());
        Registration.RegCheck regCheck = registration.getCheck().get();
        assertTrue(regCheck.getGrpc().isPresent());
        assertEquals(remoteAddress.getHost() + ":" + remoteAddress.getPort(), regCheck.getGrpc().get());
    }

    private List<ServiceHealth> mockHealth() {
        List<ServiceHealth> result = new LinkedList<>();
        result.add(mockSelfService());
        result.add(mockNotSelfService());
        result.add(mockNullServiceAddress());
        return result;
    }

    private ServiceHealth mockNotSelfService() {
        ServiceHealth serviceHealth = mock(ServiceHealth.class);
        Service service = mock(Service.class);

        when(service.getAddress()).thenReturn(remoteAddress.getHost());
        when(service.getPort()).thenReturn(remoteAddress.getPort());

        when(serviceHealth.getService()).thenReturn(service);

        return serviceHealth;
    }

    private ServiceHealth mockSelfService() {
        ServiceHealth serviceHealth = mock(ServiceHealth.class);
        Service service = mock(Service.class);

        when(service.getAddress()).thenReturn(selfRemoteAddress.getHost());
        when(service.getPort()).thenReturn(selfRemoteAddress.getPort());

        when(serviceHealth.getService()).thenReturn(service);

        return serviceHealth;
    }

    private ServiceHealth mockNullServiceAddress() {
        ServiceHealth serviceHealth = mock(ServiceHealth.class);
        Service service = mock(Service.class);

        when(serviceHealth.getService()).thenReturn(service);

        when(service.getAddress()).thenReturn("");

        return serviceHealth;
    }
}