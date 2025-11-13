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

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.DynamicEndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy;
import com.linecorp.armeria.internal.shaded.guava.base.Strings;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class KubernetesLabelSelectorEndpointGroup extends DynamicEndpointGroup {

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final Map<String, String> labelSelector;
    private final int port;
    private final String portName;
    private final SharedIndexInformer<Pod> podInformer;
    private final String selfUid;
    @Getter
    private Endpoint selfEndpoint;

    private KubernetesLabelSelectorEndpointGroup(Builder builder) {
        super(builder.selectionStrategy);
        this.kubernetesClient = builder.kubernetesClient;
        this.namespace = builder.namespace;
        this.labelSelector = builder.labelSelector;
        this.port = builder.port;
        this.portName = builder.portName;
        this.selfUid = Strings.nullToEmpty(builder.selfUid);

        this.podInformer = kubernetesClient.pods()
                .inNamespace(namespace)
                .withLabels(labelSelector)
                .inform(new PodEventHandler());

        updateEndpoints();
    }

    public static Builder builder(KubernetesClient kubernetesClient) {
        return new Builder(kubernetesClient);
    }

    @Override
    protected void doCloseAsync(CompletableFuture<?> future) {
        if (podInformer != null) {
            try {
                podInformer.close();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            future.complete(null);
        }
    }

    private void updateEndpoints() {
        try {
            if (podInformer == null) {
                log.warn("Pod informer is not initialized yet.");
                return;
            }
            final var podLister = new Lister<>(podInformer.getIndexer());
            final var pods = podLister.namespace(namespace).list();
            final List<Endpoint> newEndpoints = new ArrayList<>();
            for (Pod pod : pods) {
                if (!isPodReady(pod)) {
                    continue;
                }
                if (StringUtil.isBlank(pod.getStatus().getPodIP())) {
                    continue;
                }
                if (pod.getMetadata().getUid().equals(selfUid)) {
                    Endpoint endpoint = createEndpoint(pod);
                    if (endpoint != null) {
                        selfEndpoint = endpoint;
                    }
                    continue;
                }
                Endpoint endpoint = createEndpoint(pod);
                if (endpoint != null) {
                    newEndpoints.add(endpoint);
                }
            }

            log.debug("Updating endpoints to: {}", newEndpoints);
            setEndpoints(newEndpoints);
        } catch (Exception e) {
            log.error("Failed to update endpoints", e);
        }
    }

    private boolean isPodReady(Pod pod) {
        final var podStatus = pod.getStatus();
        if (podStatus == null) {
            return false;
        }
        if (!"Running".equalsIgnoreCase(podStatus.getPhase())) {
            return false;
        }
        if (podStatus.getContainerStatuses() == null || podStatus.getContainerStatuses().isEmpty()) {
            return false;
        }
        if (podStatus.getConditions() == null || podStatus.getConditions().isEmpty()) {
            return false;
        }

        final var allContainersReady =
            podStatus.getContainerStatuses()
                .stream().allMatch(containerStatus -> containerStatus.getReady() != Boolean.FALSE);
        final var podReadyCondition =
            podStatus.getConditions()
                .stream()
                .anyMatch(condition -> "Ready".equalsIgnoreCase(condition.getType())
                    && condition.getStatus() != null
                    && condition.getStatus().equalsIgnoreCase("True"));
        return allContainersReady && podReadyCondition;
    }

    private Endpoint createEndpoint(Pod pod) {
        final var podIP = pod.getStatus().getPodIP();
        if (StringUtil.isBlank(podIP)) {
            return null;
        }

        final var targetPort = determineTargetPort(pod);
        if (targetPort <= 0) {
            log.warn("Could not determine target port for pod: {}", pod.getMetadata().getName());
            return null;
        }

        return Endpoint.of(podIP, targetPort);
    }

    private int determineTargetPort(Pod pod) {
        if (port > 0) {
            return port;
        }

        if (StringUtil.isNotBlank(portName) && pod.getSpec().getContainers() != null) {
            return pod.getSpec().getContainers().stream()
                    .flatMap(container -> container.getPorts() != null ? container.getPorts().stream() : null)
                    .filter(containerPort -> portName.equals(containerPort.getName()))
                    .mapToInt(containerPort -> containerPort.getContainerPort())
                    .findFirst()
                    .orElse(-1);
        }

        return -1;
    }

    private class PodEventHandler implements ResourceEventHandler<Pod> {
        @Override
        public void onAdd(Pod pod) {
            log.debug("Pod added: {}", pod.getMetadata().getName());
            updateEndpoints();
        }

        @Override
        public void onUpdate(Pod oldPod, Pod newPod) {
            log.debug("Pod updated: {}, {}", newPod.getMetadata().getName(), newPod.getStatus());
            updateEndpoints();
        }

        @Override
        public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
            log.debug("Pod deleted: {}", pod.getMetadata().getName());
            updateEndpoints();
        }
    }

    @Data
    @Accessors(fluent = true)
    public static class Builder {
        private final KubernetesClient kubernetesClient;
        private String namespace = "default";
        private Map<String, String> labelSelector = new ConcurrentHashMap<>();
        private int port = -1;
        private String portName;
        private EndpointSelectionStrategy selectionStrategy = EndpointSelectionStrategy.weightedRoundRobin();
        private String selfUid;

        private Builder(KubernetesClient kubernetesClient) {
            this.kubernetesClient = kubernetesClient;
        }

        public KubernetesLabelSelectorEndpointGroup build() {
            if (port <= 0 && StringUtil.isBlank(portName)) {
                throw new IllegalArgumentException("Either port or portName must be specified");
            }
            if (labelSelector.isEmpty()) {
                throw new IllegalArgumentException("Label selector must not be empty");
            }
            return new KubernetesLabelSelectorEndpointGroup(this);
        }
    }
}
