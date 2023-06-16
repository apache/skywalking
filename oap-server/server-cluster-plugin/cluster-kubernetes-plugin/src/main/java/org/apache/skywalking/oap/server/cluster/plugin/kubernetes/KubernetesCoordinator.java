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
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.apache.skywalking.oap.server.cluster.plugin.kubernetes.EventType.ADDED;
import static org.apache.skywalking.oap.server.cluster.plugin.kubernetes.EventType.DELETED;
import static org.apache.skywalking.oap.server.cluster.plugin.kubernetes.EventType.MODIFIED;


/**
 * Read collector pod info from api-server of kubernetes, then using all containerIp list to construct the list of
 * {@link RemoteInstance}.
 */
@Slf4j
public class KubernetesCoordinator extends ClusterCoordinator {
    private final ModuleDefineHolder manager;
    private final String uid;
    private volatile int port = -1;
    private HealthCheckMetrics healthChecker;
    private ClusterModuleKubernetesConfig config;
    private final Map<String, RemoteInstance> remoteInstanceMap;
    private volatile List<String> latestInstances;

    public KubernetesCoordinator(final ModuleDefineHolder manager,
                                 final ClusterModuleKubernetesConfig config) {
        this.uid = new UidEnvSupplier(config.getUidEnvName()).get();
        this.manager = manager;
        this.config = config;
        this.remoteInstanceMap = new ConcurrentHashMap<>(20);
        this.latestInstances = new ArrayList<>(20);
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        try {
            List<Pod> pods = NamespacedPodListInformer.INFORMER.listPods().orElseGet(this::selfPod);
            if (log.isDebugEnabled()) {
                List<String> uidList = pods
                    .stream()
                    .map(item -> item.getMetadata().getUid())
                    .collect(Collectors.toList());
                log.debug("[kubernetes cluster pods uid list]:{}", uidList);
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
            this.latestInstances = remoteInstances.stream().map(it -> it.getAddress().toString()).collect(Collectors.toList());
            if (log.isDebugEnabled()) {
                remoteInstances.forEach(instance -> log.debug("kubernetes cluster instance: {}", instance));
            }
            return remoteInstances;
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }
    }

    @Override
    public void registerRemote(final RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
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

    private List<Pod> selfPod() {
        Pod v1Pod = new Pod();
        v1Pod.setMetadata(new ObjectMeta());
        v1Pod.setStatus(new PodStatus());
        v1Pod.getMetadata().setUid(uid);
        v1Pod.getStatus().setPodIP("127.0.0.1");
        return Collections.singletonList(v1Pod);
    }

    @Override
    public void start() {
        initHealthChecker();
        NamespacedPodListInformer.INFORMER.init(config, new K8sResourceEventHandler());
    }

    class K8sResourceEventHandler implements ResourceEventHandler<Pod> {

        @Override
        public void onAdd(final Pod obj) {
            updateRemoteInstances(obj, ADDED);
        }

        @Override
        public void onUpdate(final Pod oldObj, final Pod newObj) {
            updateRemoteInstances(newObj, MODIFIED);
        }

        @Override
        public void onDelete(final Pod obj, final boolean deletedFinalStateUnknown) {
            updateRemoteInstances(obj, DELETED);
        }
    }

    /**
     * When a remote instance up/off line, will receive multi event according to the pod status.
     * To avoid notify the watchers too frequency, here use a `remoteInstanceMap` to cache them.
     * Only notify watchers once when the instances changed.
     */
    private void updateRemoteInstances(Pod pod, EventType event) {
        try {
            initHealthChecker();
            if (StringUtil.isNotBlank(pod.getStatus().getPodIP())) {
                if (port == -1) {
                    port = manager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort();
                }

                RemoteInstance remoteInstance = new RemoteInstance(
                    new Address(pod.getStatus().getPodIP(), this.port, pod.getMetadata().getUid().equals(uid)));
                switch (event) {
                    case ADDED:
                    case MODIFIED:
                        if (KubernetesPods.isReady(pod)) {
                            remoteInstanceMap.put(remoteInstance.getAddress().toString(), remoteInstance);
                        } else {
                            remoteInstanceMap.remove(remoteInstance.getAddress().toString());
                        }
                        break;
                    case DELETED:
                        this.remoteInstanceMap.remove(remoteInstance.getAddress().toString());
                        break;
                    default:
                        return;
                }
                updateRemoteInstances();
            }
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            log.error("Failed to notify RemoteInstances update.", e);
        }
    }

    private void updateRemoteInstances() {
        List<String> updatedInstances = new ArrayList<>(this.remoteInstanceMap.keySet());
        if (this.latestInstances.size() != updatedInstances.size() || !this.latestInstances.containsAll(updatedInstances)) {
            List<RemoteInstance> remoteInstances = new ArrayList<>(this.remoteInstanceMap.values());
            this.latestInstances = updatedInstances;
            checkHealth(remoteInstances);
            notifyWatchers(remoteInstances);
        }
    }

    private void checkHealth(List<RemoteInstance> remoteInstances) {
        ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
        if (healthStatus.isHealth()) {
            this.healthChecker.health();
        } else {
            this.healthChecker.unHealth(healthStatus.getReason());
        }
    }
}
