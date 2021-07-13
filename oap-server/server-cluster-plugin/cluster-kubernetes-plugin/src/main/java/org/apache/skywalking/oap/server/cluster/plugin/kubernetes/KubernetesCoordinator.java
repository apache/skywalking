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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Read collector pod info from api-server of kubernetes, then using all containerIp list to construct the list of
 * {@link RemoteInstance}.
 */
@Slf4j
public class KubernetesCoordinator implements ClusterRegister, ClusterNodesQuery {

    private final ModuleDefineHolder manager;
    private final String uid;
    private volatile int port = -1;
    private HealthCheckMetrics healthChecker;

    public KubernetesCoordinator(final ModuleDefineHolder manager,
                                 final ClusterModuleKubernetesConfig config) {
        this.uid = new UidEnvSupplier(config.getUidEnvName()).get();
        this.manager = manager;
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        try {
            initHealthChecker();
            List<V1Pod> pods = NamespacedPodListInformer.INFORMER.listPods().orElseGet(this::selfPod);
            if (log.isDebugEnabled()) {
                List<String> uidList = pods
                    .stream()
                    .map(item -> item.getMetadata().getUid())
                    .collect(Collectors.toList());
                log.debug("[kubernetes cluster pods uid list]:{}", uidList.toString());
            }
            if (port == -1) {
                port = manager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort();
            }
            List<RemoteInstance> remoteInstances =
                pods.stream()
                    .filter(pod -> StringUtil.isNotBlank(pod.getStatus().getPodIP()))
                    .map(pod -> new RemoteInstance(
                        new Address(pod.getStatus().getPodIP(), port, pod.getMetadata().getUid().equals(uid))))
                    .collect(Collectors.toList());
            healthChecker.health();
            return remoteInstances;
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }
    }

    @Override
    public void registerRemote(final RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
            initHealthChecker();
            this.port = remoteInstance.getAddress().getPort();
            healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME)
                                                  .provider()
                                                  .getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge(
                "cluster_k8s", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    private List<V1Pod> selfPod() {
        V1Pod v1Pod = new V1Pod();
        v1Pod.setMetadata(new V1ObjectMeta());
        v1Pod.setStatus(new V1PodStatus());
        v1Pod.getMetadata().setUid(uid);
        v1Pod.getStatus().setPodIP("127.0.0.1");
        return Collections.singletonList(v1Pod);
    }
}
