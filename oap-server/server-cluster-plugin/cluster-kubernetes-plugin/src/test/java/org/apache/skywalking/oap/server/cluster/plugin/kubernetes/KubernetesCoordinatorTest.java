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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberModifier;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
@PrepareForTest({NamespacedPodListInformer.class})
public class KubernetesCoordinatorTest {

    private KubernetesCoordinator coordinator;
    private HealthCheckMetrics healthChecker = mock(HealthCheckMetrics.class);

    public static final String LOCAL_HOST = "127.0.0.1";
    public static final Integer GRPC_PORT = 8454;
    public static final Integer SELF_UID = 12345;

    private Address selfAddress;
    private NamespacedPodListInformer informer;

    @Before
    public void prepare() throws IllegalAccessException {
        coordinator = new KubernetesCoordinator(getManager(), new ClusterModuleKubernetesConfig());
        Whitebox.setInternalState(coordinator, "healthChecker", healthChecker);
        MemberModifier.field(KubernetesCoordinator.class, "uid").set(coordinator, String.valueOf(SELF_UID));
        selfAddress = new Address(LOCAL_HOST, GRPC_PORT, true);
        informer = PowerMockito.mock(NamespacedPodListInformer.class);
        Whitebox.setInternalState(NamespacedPodListInformer.class, "INFORMER", informer);
        doNothing().when(healthChecker).health();
    }

    @Test
    public void queryRemoteNodesWhenInformerNotwork() throws Exception {
        PowerMockito.doReturn(Optional.empty()).when(NamespacedPodListInformer.INFORMER).listPods();
        List<RemoteInstance> remoteInstances = Whitebox.invokeMethod(coordinator, "queryRemoteNodes");
        Assert.assertEquals(1, remoteInstances.size());
        Assert.assertEquals(selfAddress, remoteInstances.get(0).getAddress());

    }

    @Test
    public void queryRemoteNodesWhenInformerWork() throws Exception {
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

    private ModuleManagerTesting getManager() {
        ModuleManagerTesting moduleManagerTesting = new ModuleManagerTesting();
        ModuleDefineTesting coreModuleDefine = new ModuleDefineTesting();
        moduleManagerTesting.put(CoreModule.NAME, coreModuleDefine);
        CoreModuleConfig config = PowerMockito.mock(CoreModuleConfig.class);
        when(config.getGRPCHost()).thenReturn(LOCAL_HOST);
        when(config.getGRPCPort()).thenReturn(GRPC_PORT);
        moduleManagerTesting.put(TelemetryModule.NAME, coreModuleDefine);
        coreModuleDefine.provider().registerServiceImplementation(ConfigService.class, new ConfigService(config));
        coreModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, new MetricsCreatorNoop());
        return moduleManagerTesting;
    }

    private List<V1Pod> mockPodList() {
        List<V1Pod> pods = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            V1Pod v1Pod = new V1Pod();
            v1Pod.setMetadata(new V1ObjectMeta());
            v1Pod.setStatus(new V1PodStatus());
            v1Pod.getMetadata().setUid(String.valueOf(SELF_UID + i));
            v1Pod.getStatus().setPodIP(LOCAL_HOST);
            pods.add(v1Pod);
        }
        return pods;
    }
}
