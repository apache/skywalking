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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.requests.EtcdKeyPutRequest;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(EtcdKeysResponse.class)
@PowerMockIgnore("javax.management.*")
public class EtcdCoordinatorTest {

    private ClusterModuleEtcdConfig etcdConfig = new ClusterModuleEtcdConfig();

    private EtcdClient client;

    private EtcdCoordinator coordinator;

    private Gson gson = new Gson();

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private Address internalAddress = new Address("10.0.0.3", 1002, false);

    private static final String SERVICE_NAME = "my-service";

    private EtcdResponsePromise<EtcdKeysResponse> getPromise;
    private EtcdResponsePromise<EtcdKeysResponse> putPromise;

    private EtcdKeysResponse response;

    private EtcdKeyPutRequest putRequest = mock(EtcdKeyPutRequest.class);

    private EtcdKeyGetRequest getRequest = mock(EtcdKeyGetRequest.class);

    private EtcdKeyPutRequest putDirRequest = mock(EtcdKeyPutRequest.class);

    private EtcdResponsePromise<EtcdKeysResponse> putDirPromise;

    @Mock
    private List<EtcdKeysResponse.EtcdNode> list = mock(List.class);

    @Before
    public void setUp() throws Exception {
        etcdConfig.setServiceName(SERVICE_NAME);

        client = mock(EtcdClient.class);
        PowerMockito.whenNew(EtcdClient.class).withAnyArguments().thenReturn(client);
        client = new EtcdClient("http://10.0.0.1:1000", "http://10.0.0.2:2000");
        coordinator = new EtcdCoordinator(etcdConfig, client);

        putPromise = (EtcdResponsePromise<EtcdKeysResponse>) mock(EtcdResponsePromise.class);
        getPromise = (EtcdResponsePromise<EtcdKeysResponse>) mock(EtcdResponsePromise.class);
        putDirPromise = (EtcdResponsePromise<EtcdKeysResponse>) mock(EtcdResponsePromise.class);

        PowerMockito.when(client.putDir(anyString())).thenReturn(putDirRequest);
        PowerMockito.when(putDirRequest.ttl(anyInt())).thenReturn(putDirRequest);
        PowerMockito.when(putDirRequest.send()).thenReturn(putDirPromise);
        PowerMockito.when(client.put(anyString(), anyString())).thenReturn(putRequest);
        PowerMockito.when(putRequest.ttl(anyInt())).thenReturn(putRequest);
        PowerMockito.when(putRequest.send()).thenReturn(putPromise);
        PowerMockito.when(client.get(anyString())).thenReturn(getRequest);
        PowerMockito.when(getRequest.send()).thenReturn(getPromise);

        response = PowerMockito.mock(EtcdKeysResponse.class);

        response = PowerMockito.mock(EtcdKeysResponse.class);
        when(putPromise.get()).thenReturn(response);
        when(getPromise.get()).thenReturn(response);
        when(putDirPromise.get()).thenReturn(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queryRemoteNodesWithNonOrEmpty() {
        EtcdKeysResponse.EtcdNode node = PowerMockito.mock(EtcdKeysResponse.EtcdNode.class);
        when(response.getNode()).thenReturn(node);
        when(node.getValue()).thenReturn("{}");
        assertEquals(0, coordinator.queryRemoteNodes().size());
        assertEquals(0, coordinator.queryRemoteNodes().size());
    }

    @Test
    public void queryRemoteNodes() {
        registerSelfRemote();

        EtcdKeysResponse.EtcdNode node = PowerMockito.mock(EtcdKeysResponse.EtcdNode.class);
        EtcdKeysResponse.EtcdNode node1 = PowerMockito.mock(EtcdKeysResponse.EtcdNode.class);

        when(response.getNode()).thenReturn(node);
        list = new ArrayList<>();
        List list1 = Mockito.spy(list);
        list1.add(node1);
        when(node.getNodes()).thenReturn(list1);
        when(node1.getValue()).thenReturn("{\"serviceId\":\"my-service\",\"host\":\"10.0.0.2\",\"port\":1001}");
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertEquals(1, remoteInstances.size());

        RemoteInstance selfInstance = remoteInstances.get(0);
        velidate(selfRemoteAddress, selfInstance);
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
        etcdConfig.setInternalComHost(internalAddress.getHost());
        etcdConfig.setInternalComPort(internalAddress.getPort());
        registerRemote(internalAddress);
    }

    private void velidate(Address originArress, RemoteInstance instance) {
        Address instanceAddress = instance.getAddress();
        assertEquals(originArress.getHost(), instanceAddress.getHost());
        assertEquals(originArress.getPort(), instanceAddress.getPort());
    }

    private void registerRemote(Address address) {
        coordinator.registerRemote(new RemoteInstance(address));
        EtcdEndpoint endpoint = afterRegister().get(0);
        verifyRegistration(address, endpoint);
    }

    private List<EtcdEndpoint> afterRegister() {
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).put(nameCaptor.capture(), argumentCaptor.capture());
        EtcdEndpoint endpoint = gson.fromJson(argumentCaptor.getValue(), EtcdEndpoint.class);
        List<EtcdEndpoint> list = new ArrayList<>();
        list.add(endpoint);
        return list;
    }

    private void verifyRegistration(Address remoteAddress, EtcdEndpoint endpoint) {
        assertNotNull(endpoint);
        assertEquals(SERVICE_NAME, endpoint.getServiceName());
        assertEquals(remoteAddress.getHost(), endpoint.getHost());
        assertEquals(remoteAddress.getPort(), endpoint.getPort());
    }

}
