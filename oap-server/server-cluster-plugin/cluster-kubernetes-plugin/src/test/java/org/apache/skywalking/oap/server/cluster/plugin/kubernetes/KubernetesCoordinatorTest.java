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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
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
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private ModuleProvider providerA;
    private Address addressA;
    private Address addressB;
    private KubernetesCoordinator coordinatorA;
    private KubernetesCoordinator coordinatorB;
    private Pod podA;
    private Pod podB;

    @BeforeEach
    public void prepare() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        NamespacedPodListInformer informer = mock(NamespacedPodListInformer.class);
        Whitebox.setInternalState(NamespacedPodListInformer.class, "INFORMER", informer);
        when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(mock(ModuleProviderHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class)).thenReturn(
            mock(ConfigService.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort()).thenReturn(
            GRPC_PORT);

        addressA = new Address(LOCAL_HOST, GRPC_PORT, true);
        addressB = new Address(REMOTE_HOST, GRPC_PORT, true);
        podA = mockPod(SELF_UID, LOCAL_HOST);
        podB = mockPod(REMOTE_UID, REMOTE_HOST);
    }

    @Test
    public void queryRemoteNodesWhenInformerNotwork() throws Exception {
        withEnvironmentVariable(SELF_UID, SELF_UID + "0").execute(() -> {
            providerA = createProvider(SELF_UID);
            coordinatorA = getClusterCoordinator(providerA);
            coordinatorA.start();
        });

        KubernetesCoordinator coordinator = getClusterCoordinator(providerA);
        doReturn(Optional.empty()).when(NamespacedPodListInformer.INFORMER).listPods();
        List<RemoteInstance> remoteInstances = Whitebox.invokeMethod(coordinator, "queryRemoteNodes");
        Assertions.assertEquals(1, remoteInstances.size());
        Assertions.assertEquals(addressA, remoteInstances.get(0).getAddress());
    }

    @Test
    public void queryRemoteNodesWhenInformerWork() throws Exception {
        withEnvironmentVariable(SELF_UID + "0", SELF_UID + "0")
                .execute(() -> {
                    ModuleProvider provider = createProvider(SELF_UID + "0");
                    KubernetesCoordinator coordinator = getClusterCoordinator(provider);
                    coordinator.start();
                    doReturn(Optional.of(mockPodList())).when(NamespacedPodListInformer.INFORMER).listPods();
                    List<RemoteInstance> remoteInstances = Whitebox.invokeMethod(coordinator, "queryRemoteNodes");
                    Assertions.assertEquals(5, remoteInstances.size());
                    List<RemoteInstance> self = remoteInstances.stream()
                            .filter(item -> item.getAddress().isSelf())
                            .collect(Collectors.toList());
                    List<RemoteInstance> others = remoteInstances.stream()
                            .filter(item -> !item.getAddress().isSelf())
                            .collect(Collectors.toList());

                    Assertions.assertEquals(1, self.size());
                    Assertions.assertEquals(4, others.size());
                });
    }

    @Test
    public void registerRemote() throws Exception {
        RemoteInstance instance = new RemoteInstance(addressA);
        withEnvironmentVariable(SELF_UID, SELF_UID).execute(() -> {
            providerA = createProvider(SELF_UID);
            coordinatorA = getClusterCoordinator(providerA);
            coordinatorA.start();
        });
        doReturn(Optional.of(Collections.singletonList(podA)))
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
        withEnvironmentVariable(SELF_UID, SELF_UID + "0").execute(() -> {
            providerA = createProvider(SELF_UID);
            coordinatorA = getClusterCoordinator(providerA);
            coordinatorA.start();
        });
        withEnvironmentVariable(REMOTE_UID, REMOTE_UID).execute(() -> {
            ModuleProvider providerB = createProvider(REMOTE_UID);
            coordinatorB = getClusterCoordinator(providerB);
        });
        coordinatorB.start();

        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        doReturn(Optional.of(Collections.singletonList(podA)))
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
        withEnvironmentVariable(SELF_UID, SELF_UID).execute(() -> {
            providerA = createProvider(SELF_UID);
            coordinatorA = getClusterCoordinator(providerA);
            coordinatorA.start();
        });
        withEnvironmentVariable(REMOTE_UID, REMOTE_UID).execute(() -> {
            ModuleProvider providerB = createProvider(REMOTE_UID);
            coordinatorB = getClusterCoordinator(providerB);
        });
        coordinatorB.start();

        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        doReturn(Optional.of(Arrays.asList(podA, podB)))
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
        withEnvironmentVariable(SELF_UID, SELF_UID).execute(() -> {
            providerA = createProvider(SELF_UID);
            coordinatorA = getClusterCoordinator(providerA);
            coordinatorA.start();
        });
        withEnvironmentVariable(REMOTE_UID, REMOTE_UID).execute(() -> {
            ModuleProvider providerB = createProvider(REMOTE_UID);
            coordinatorB = getClusterCoordinator(providerB);
        });
        coordinatorB.start();

        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);

        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);

        doReturn(Optional.of(Arrays.asList(podA, podB)))
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
        doReturn(Optional.of(Collections.singletonList(podB)))
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

    private ClusterModuleKubernetesProvider createProvider(String uidEnvName) {
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

    private Pod mockPod(String uid, String ip) {
        Pod v1Pod = new Pod();
        v1Pod.setMetadata(new ObjectMeta());
        v1Pod.setStatus(new PodStatus());
        v1Pod.getStatus().setPhase("Running");
        v1Pod.getMetadata().setUid(uid);
        v1Pod.getStatus().setPodIP(ip);
        return v1Pod;
    }

    private List<Pod> mockPodList() {
        List<Pod> pods = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Pod v1Pod = new Pod();
            v1Pod.setMetadata(new ObjectMeta());
            v1Pod.setStatus(new PodStatus());
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

    static class ClusterMockWatcher implements ClusterWatcher {
        @Getter
        private List<RemoteInstance> remoteInstances = new ArrayList<>();

        @Override
        public void onClusterNodesChanged(final List<RemoteInstance> remoteInstances) {
            this.remoteInstances = remoteInstances;
        }
    }
}
