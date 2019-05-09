package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author caoyixiong
 */
public class NacosCoordinatorTest {
    private NamingService namingService = mock(NamingService.class);
    private ClusterModuleNacosConfig nacosConfig = new ClusterModuleNacosConfig();
    private NacosCoordinator coordinator;

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private static final String SERVICE_NAME = "test-service";

    @Before
    public void setUp() throws NacosException {
        nacosConfig.setServiceName(SERVICE_NAME);
        coordinator = new NacosCoordinator(namingService, nacosConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queryRemoteNodesWithNonOrEmpty() throws NacosException {
        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(null, Collections.emptyList());
        assertEquals(0, coordinator.queryRemoteNodes().size());
    }

    @Test
    public void queryRemoteNodes() throws NacosException {
        registerSelfRemote();
        List<Instance> instances = mockInstance();
        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(instances);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertEquals(2, remoteInstances.size());

        RemoteInstance selfInstance = remoteInstances.get(0);
        validate(selfRemoteAddress, selfInstance);

        RemoteInstance notSelfInstance = remoteInstances.get(1);
        validate(remoteAddress, notSelfInstance);
    }

    @Test
    public void queryRemoteNodesWithNullSelf() throws NacosException {
        List<Instance> instances = mockInstance();
        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(instances);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertTrue(remoteInstances.isEmpty());
    }

    @Test
    public void registerRemote() throws NacosException {
        registerRemote(remoteAddress);
    }

    @Test
    public void registerSelfRemote() throws NacosException {
        registerRemote(selfRemoteAddress);
    }

    private void validate(Address originArress, RemoteInstance instance) {
        Address instanceAddress = instance.getAddress();
        assertEquals(originArress.getHost(), instanceAddress.getHost());
        assertEquals(originArress.getPort(), instanceAddress.getPort());
    }

    private void registerRemote(Address address) throws NacosException {
        coordinator.registerRemote(new RemoteInstance(address));

        ArgumentCaptor<String> serviceNameArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hostArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(namingService).registerInstance(serviceNameArgumentCaptor.capture(), hostArgumentCaptor.capture(), portArgumentCaptor.capture());

        assertEquals(SERVICE_NAME, serviceNameArgumentCaptor.getValue());
        assertEquals(address.getHost(), hostArgumentCaptor.getValue());
        assertEquals(Long.valueOf(address.getPort()), Long.valueOf(portArgumentCaptor.getValue()));
    }

    private List<Instance> mockInstance() {
        Instance remoteInstance= new Instance();
        Instance selfInstance = new Instance();
        selfInstance.setIp(selfRemoteAddress.getHost());
        selfInstance.setPort(selfRemoteAddress.getPort());

        remoteInstance.setIp(remoteAddress.getHost());
        remoteInstance.setPort(remoteAddress.getPort());

        List<Instance> instances = new ArrayList<>();
        instances.add(selfInstance);
        instances.add(remoteInstance);
        return instances;
    }
}
