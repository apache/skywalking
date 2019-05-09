package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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

    private List<Instance> instances;

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private Address internalAddress = new Address("10.0.0.3", 1002, false);

    private static final String SERVICE_NAME = "test-service";

    @Before
    public void setUp() throws NacosException {

        nacosConfig.setServiceName(SERVICE_NAME);

        coordinator = new NacosCoordinator(namingService, nacosConfig);

        instances = mock(List.class);

        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(instances);
    }


    @Test
    @SuppressWarnings("unchecked")
    public void queryRemoteNodesWithNonOrEmpty() throws NacosException {
        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(null, Collections.emptyList());
        assertEquals(0, coordinator.queryRemoteNodes().size());
        assertEquals(0, coordinator.queryRemoteNodes().size());
    }

    @Test
    public void queryRemoteNodes() throws NacosException {
        registerSelfRemote();
        List<Instance> instances = mockHealth();
        when(namingService.selectInstances(anyString(), anyBoolean())).thenReturn(serviceHealths);
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
        assertTrue(remoteInstances.isEmpty());
    }

    @Test
    public void registerRemote() {
        registerRemote(remoteAddress);
    }

    @Test
    public void registerSelfRemote() {
        registerRemote(selfRemoteAddress);
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
}
