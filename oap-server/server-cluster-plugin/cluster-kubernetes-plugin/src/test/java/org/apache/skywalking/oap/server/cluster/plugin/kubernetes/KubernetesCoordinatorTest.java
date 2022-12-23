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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterWatcher;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberModifier;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
    "com.sun.org.apache.xerces.*",
    "javax.xml.*",
    "org.xml.*",
    "javax.management.*",
    "org.w3c.*"
})
@PrepareForTest({NamespacedPodListInformer.class})
public class KubernetesCoordinatorTest {

    public static final String LOCAL_HOST = "127.0.0.1";
    public static final String REMOTE_HOST = "127.0.0.2";
    public static final Integer GRPC_PORT = 11800;
    public static final String SELF_UID = "self";
    public static final String REMOTE_UID = "remote";

    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;
    private NamespacedPodListInformer informer;
    private ModuleProvider providerA;
    private ModuleProvider providerB;
    private Address addressA;
    private Address addressB;
    private KubernetesCoordinator coordinatorA;
    private KubernetesCoordinator coordinatorB;
    private V1Pod podA;
    private V1Pod podB;

    @Before
    public void prepare() throws ModuleStartException {

        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        informer = PowerMockito.mock(NamespacedPodListInformer.class);
        Whitebox.setInternalState(NamespacedPodListInformer.class, "INFORMER", informer);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(mock(ModuleProviderHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class)).thenReturn(
            mock(ConfigService.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort()).thenReturn(
            GRPC_PORT);

        providerA = createProvider(SELF_UID);
        providerB = createProvider(REMOTE_UID);
        addressA = new Address(LOCAL_HOST, GRPC_PORT, true);
        addressB = new Address(REMOTE_HOST, GRPC_PORT, true);
        podA = mockPod(SELF_UID, LOCAL_HOST);
        podB = mockPod(REMOTE_UID, REMOTE_HOST);
        coordinatorA = getClusterCoordinator(providerA);
        coordinatorB = getClusterCoordinator(providerB);
        coordinatorA.start();
        coordinatorB.start();
    }

    @Test
    public void queryRemoteNodesWhenInformerNotwork() throws Exception {
        KubernetesCoordinator coordinator = getClusterCoordinator(providerA);
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorA, SELF_UID);
        PowerMockito.doReturn(Optional.empty()).when(NamespacedPodListInformer.INFORMER).listPods();
        List<RemoteInstance> remoteInstances = Whitebox.invokeMethod(coordinator, "queryRemoteNodes");
        Assert.assertEquals(1, remoteInstances.size());
        Assert.assertEquals(addressA, remoteInstances.get(0).getAddress());

    }

    @Test
    public void queryRemoteNodesWhenInformerWork() throws Exception {
        ModuleProvider provider = createProvider(SELF_UID + "0");
        KubernetesCoordinator coordinator = getClusterCoordinator(provider);
        coordinator.start();
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinator, SELF_UID + "0");
        PowerMockito.doReturn(Optional.of(mockPodList())).when(NamespacedPodListInformer.INFORMER).listPods();
        List<RemoteInstance> remoteInstances = Whitebox.invokeMethod(coordinator, "queryRemoteNodes");
        Assert.assertEquals(5, remoteInstances.size());
        List<RemoteInstance> self = remoteInstances.stream()
                                                   .filter(item -> item.getAddress().isSelf())
                                                   .collect(Collectors.toList());
        List<RemoteInstance> others = remoteInstances.stream()
                                                     .filter(item -> !item.getAddress().isSelf())
                                                     .collect(Collectors.toList());

        Assert.assertEquals(1, self.size());
        Assert.assertEquals(4, others.size());

    }

    @Test
    public void registerRemote() throws Exception {
        RemoteInstance instance = new RemoteInstance(addressA);
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorA, SELF_UID);
        PowerMockito.doReturn(Optional.of(Collections.singletonList(podA)))
                    .when(NamespacedPodListInformer.INFORMER)
                    .listPods();

        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcher);
        coordinatorA.registerRemote(instance);
        KubernetesCoordinator.K8sResourceEventHandler listener = coordinatorA.new K8sResourceEventHandler();
        listener.onAdd(podA);

        List<RemoteInstance> remoteInstances = watcher.getRemoteInstances();
        assertEquals(1, remoteInstances.size());
        assertEquals(1, coordinatorA.queryRemoteNodes().size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(addressA, queryAddress);
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorB, REMOTE_UID);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        PowerMockito.doReturn(Optional.of(Collections.singletonList(podA)))
                    .when(NamespacedPodListInformer.INFORMER)
                    .listPods();
        RemoteInstance instance = new RemoteInstance(addressA);
        coordinatorA.registerRemote(instance);
        KubernetesCoordinator.K8sResourceEventHandler listener = coordinatorB.new K8sResourceEventHandler();
        listener.onAdd(podA);

        // Receiver
        List<RemoteInstance> remoteInstances = watcherB.getRemoteInstances();
        assertEquals(1, remoteInstances.size());
        assertEquals(1, coordinatorB.queryRemoteNodes().size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(addressA, queryAddress);
        assertFalse(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfCluster() throws Exception {
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorA, SELF_UID);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorB, REMOTE_UID);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        PowerMockito.doReturn(Optional.of(Arrays.asList(podA, podB)))
                    .when(NamespacedPodListInformer.INFORMER)
                    .listPods();
        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);
        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        KubernetesCoordinator.K8sResourceEventHandler listenerA = coordinatorA.new K8sResourceEventHandler();
        listenerA.onAdd(podA);
        listenerA.onAdd(podB);
        KubernetesCoordinator.K8sResourceEventHandler listenerB = coordinatorB.new K8sResourceEventHandler();
        listenerB.onAdd(podA);
        listenerB.onAdd(podB);

        List<RemoteInstance> remoteInstancesOfA = watcherA.getRemoteInstances();
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2, coordinatorA.queryRemoteNodes().size());

        List<RemoteInstance> remoteInstancesOfB = watcherB.getRemoteInstances();
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2, coordinatorB.queryRemoteNodes().size());
    }

    @Test
    public void deregisterRemoteOfCluster() throws Exception {
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorA, SELF_UID);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);

        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinatorB, REMOTE_UID);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        PowerMockito.doReturn(Optional.of(Arrays.asList(podA, podB)))
                    .when(NamespacedPodListInformer.INFORMER)
                    .listPods();
        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);
        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        KubernetesCoordinator.K8sResourceEventHandler listenerA = coordinatorA.new K8sResourceEventHandler();
        listenerA.onAdd(podA);
        listenerA.onAdd(podB);
        KubernetesCoordinator.K8sResourceEventHandler listenerB = coordinatorB.new K8sResourceEventHandler();
        listenerB.onAdd(podA);
        listenerB.onAdd(podB);

        List<RemoteInstance> remoteInstancesOfA = watcherA.getRemoteInstances();
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2, coordinatorA.queryRemoteNodes().size());

        List<RemoteInstance> remoteInstancesOfB = watcherB.getRemoteInstances();
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2, coordinatorB.queryRemoteNodes().size());

        // deregister A
        listenerB.onDelete(podA, false);
        PowerMockito.doReturn(Optional.of(Collections.singletonList(podB)))
                    .when(NamespacedPodListInformer.INFORMER)
                    .listPods();
        // only B
        remoteInstancesOfB = watcherB.getRemoteInstances();
        assertEquals(1, remoteInstancesOfB.size());
        assertEquals(1, coordinatorB.queryRemoteNodes().size());

        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(addressB, address);
        assertTrue(address.isSelf());
    }

    private ClusterModuleKubernetesProvider createProvider(String uidEnvName)
        throws ModuleStartException {
        ClusterModuleKubernetesProvider provider = new ClusterModuleKubernetesProvider();

        ClusterModuleKubernetesConfig config = new ClusterModuleKubernetesConfig();
        provider.newConfigCreator().onInitialized(config);
        config.setNamespace("default");
        config.setLabelSelector("app=oap");
        config.setUidEnvName(uidEnvName);

        provider.setManager(moduleManager);
        provider.prepare();
        provider.start();
        provider.notifyAfterCompleted();
        return provider;
    }

    private KubernetesCoordinator getClusterCoordinator(ModuleProvider provider) {
        return (KubernetesCoordinator) provider.getService(ClusterCoordinator.class);
    }

    private V1Pod mockPod(String uid, String ip) {
        V1Pod v1Pod = new V1Pod();
        v1Pod.setMetadata(new V1ObjectMeta());
        v1Pod.setStatus(new V1PodStatus());
        v1Pod.getStatus().setPhase("Running");
        v1Pod.getMetadata().setUid(uid);
        v1Pod.getStatus().setPodIP(ip);
        return v1Pod;
    }

    private List<V1Pod> mockPodList() {
        List<V1Pod> pods = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            V1Pod v1Pod = new V1Pod();
            v1Pod.setMetadata(new V1ObjectMeta());
            v1Pod.setStatus(new V1PodStatus());
            v1Pod.getMetadata().setUid(SELF_UID + i);
            v1Pod.getStatus().setPodIP(LOCAL_HOST);
            pods.add(v1Pod);
        }
        return pods;
    }

    private void validateServiceInstance(Address selfAddress, Address otherAddress,
                                         List<RemoteInstance> queryResult) {
        assertEquals(2, queryResult.size());

        boolean selfExist = false, otherExist = false;

        for (RemoteInstance instance : queryResult) {
            Address queryAddress = instance.getAddress();
            if (queryAddress.equals(selfAddress) && queryAddress.isSelf()) {
                selfExist = true;
            } else if (queryAddress.equals(otherAddress) && !queryAddress.isSelf()) {
                otherExist = true;
            }
        }

        assertTrue(selfExist);
        assertTrue(otherExist);
    }

    class ClusterMockWatcher implements ClusterWatcher {
        @Getter
        private List<RemoteInstance> remoteInstances = new ArrayList<>();

        @Override
        public void onClusterNodesChanged(final List<RemoteInstance> remoteInstances) {
            this.remoteInstances = remoteInstances;
        }
    }
}
