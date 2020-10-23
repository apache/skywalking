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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

@Slf4j
class K8SServiceRegistry {
    final Map<String, ServiceMetaInfo> ipServiceMap;

    final ExecutorService executor;

    K8SServiceRegistry() {
        ipServiceMap = new ConcurrentHashMap<>();
        executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("K8SServiceRegistry-%d")
                .setDaemon(true)
                .build()
        );
    }

    void start() throws IOException {
        final ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient()
                                         .newBuilder()
                                         .readTimeout(0, TimeUnit.SECONDS)
                                         .build());

        final CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        final SharedInformerFactory factory = new SharedInformerFactory(executor);

        listenEndpointsEvents(coreV1Api, factory);
        listenPodEvents(coreV1Api, factory);

        factory.startAllRegisteredInformers();
    }

    private void listenEndpointsEvents(final CoreV1Api coreV1Api, final SharedInformerFactory factory) {
        factory.sharedIndexInformerFor(
            params -> coreV1Api.listEndpointsForAllNamespacesCall(
                null,
                null,
                null,
                null,
                null,
                null,
                params.resourceVersion,
                params.timeoutSeconds,
                params.watch,
                null
            ),
            V1Endpoints.class,
            V1EndpointsList.class
        ).addEventHandler(new ResourceEventHandler<V1Endpoints>() {
            @Override
            public void onAdd(final V1Endpoints endpoints) {
                addEndpoints(endpoints);
            }

            @Override
            public void onUpdate(final V1Endpoints oldEndpoints, final V1Endpoints newEndpoints) {
                addEndpoints(newEndpoints);
            }

            @Override
            public void onDelete(final V1Endpoints endpoints, final boolean deletedFinalStateUnknown) {
                removeEndpoints(endpoints);
            }
        });
    }

    private void listenPodEvents(final CoreV1Api coreV1Api, final SharedInformerFactory factory) {
        factory.sharedIndexInformerFor(
            params -> coreV1Api.listPodForAllNamespacesCall(
                null,
                null,
                null,
                null,
                null,
                null,
                params.resourceVersion,
                params.timeoutSeconds,
                params.watch,
                null
            ),
            V1Pod.class,
            V1PodList.class
        ).addEventHandler(new ResourceEventHandler<V1Pod>() {
            @Override
            public void onAdd(final V1Pod pod) {
                addPod(pod);
            }

            @Override
            public void onUpdate(final V1Pod oldPod, final V1Pod newPod) {
                addPod(newPod);
            }

            @Override
            public void onDelete(final V1Pod pod, final boolean deletedFinalStateUnknown) {
                removePod(pod);
            }
        });
    }

    private void removePod(final V1Pod pod) {
        log.debug("Removing pod {}", pod);

        Optional.ofNullable(pod.getStatus()).ifPresent(
            status -> ipServiceMap.remove(status.getPodIP())
        );
    }

    private void addPod(final V1Pod pod) {
        log.debug("Adding pod {}", pod);

        Optional.ofNullable(pod.getStatus()).ifPresent(
            status -> {
                final String ip = status.getPodIP();
                final ServiceMetaInfo service = ipServiceMap.computeIfAbsent(ip, unused -> new ServiceMetaInfo());

                final V1ObjectMeta podMeta = requireNonNull(pod.getMetadata());
                service.setServiceInstanceName(String.format("%s.%s", podMeta.getName(), podMeta.getNamespace()));
                service.setTags(transformLabelsToTags(podMeta.getLabels()));
            }
        );
    }

    private void addEndpoints(final V1Endpoints endpoints) {
        log.debug("Adding endpoints {}", endpoints);

        final String serviceName = requireNonNull(endpoints.getMetadata()).getName();

        requireNonNull(endpoints.getSubsets()).forEach(subset -> {
            requireNonNull(subset.getAddresses()).forEach(address -> {
                final String ip = address.getIp();
                final ServiceMetaInfo service = ipServiceMap.computeIfAbsent(ip, unused -> new ServiceMetaInfo());
                service.setServiceName(serviceName);
            });
        });
    }

    private void removeEndpoints(final V1Endpoints endpoints) {
        log.debug("Removing endpoints {}", endpoints);

        requireNonNull(endpoints.getSubsets()).forEach(subset -> {
            requireNonNull(subset.getAddresses()).forEach(address -> {
                final String ip = address.getIp();
                ipServiceMap.remove(ip);
            });
        });
    }

    private List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final Map<String, String> labels) {
        if (isNull(labels)) {
            return Collections.emptyList();
        }
        return labels.entrySet()
                     .stream()
                     .map(each -> new ServiceMetaInfo.KeyValue(each.getKey(), each.getValue()))
                     .collect(Collectors.toList());
    }

    ServiceMetaInfo findService(final String ip) {
        final ServiceMetaInfo service = ipServiceMap.getOrDefault(ip, ServiceMetaInfo.UNKNOWN);
        if (!service.isComplete()) {
            log.debug("Unknown ip {}, ip -> service is null, service {}", ip, service);
            return ServiceMetaInfo.UNKNOWN;
        }
        return service;
    }

    boolean isEmpty() {
        return ipServiceMap.isEmpty();
    }
}
