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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
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
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

@Slf4j
public class K8SServiceRegistry {
    protected final Map<String/* ip */, ServiceMetaInfo> ipServiceMetaInfoMap;

    protected final Map<String/* namespace:serviceName */, V1Service> idServiceMap;

    protected final Map<String/* ip */, V1Pod> ipPodMap;

    protected final Map<String/* ip */, String/* namespace:serviceName */> ipServiceMap;

    protected final ExecutorService executor;

    protected final ServiceNameFormatter serviceNameFormatter;

    public K8SServiceRegistry(final EnvoyMetricReceiverConfig config) {
        serviceNameFormatter = new ServiceNameFormatter(config.getK8sServiceNameRule());
        ipServiceMetaInfoMap = new ConcurrentHashMap<>();
        idServiceMap = new ConcurrentHashMap<>();
        ipPodMap = new ConcurrentHashMap<>();
        ipServiceMap = new ConcurrentHashMap<>();
        executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("K8SServiceRegistry-%d")
                .setDaemon(true)
                .build()
        );
    }

    public void start() throws IOException {
        final ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient()
                                         .newBuilder()
                                         .readTimeout(0, TimeUnit.SECONDS)
                                         .build());
        Configuration.setDefaultApiClient(apiClient);

        final CoreV1Api coreV1Api = new CoreV1Api();
        final SharedInformerFactory factory = new SharedInformerFactory(executor);

        // TODO: also listen to the EndpointSlice event after the client supports us to do so
        listenServiceEvents(coreV1Api, factory);
        listenEndpointsEvents(coreV1Api, factory);
        listenPodEvents(coreV1Api, factory);

        factory.startAllRegisteredInformers();
    }

    private void listenServiceEvents(final CoreV1Api coreV1Api, final SharedInformerFactory factory) {
        factory.sharedIndexInformerFor(
            params -> coreV1Api.listServiceForAllNamespacesCall(
                null,
                null,
                null,
                null,
                null,
                null,
                params.resourceVersion,
                300,
                params.watch,
                null
            ),
            V1Service.class,
            V1ServiceList.class
        ).addEventHandler(new ResourceEventHandler<V1Service>() {
            @Override
            public void onAdd(final V1Service service) {
                addService(service);
            }

            @Override
            public void onUpdate(final V1Service oldService, final V1Service newService) {
                addService(newService);
            }

            @Override
            public void onDelete(final V1Service service, final boolean deletedFinalStateUnknown) {
                removeService(service);
            }
        });
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
                300,
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
                300,
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

    protected void addService(final V1Service service) {
        Optional.ofNullable(service.getMetadata()).ifPresent(
            metadata -> idServiceMap.put(metadata.getNamespace() + ":" + metadata.getName(), service)
        );

        recompose();
    }

    protected void removeService(final V1Service service) {
        Optional.ofNullable(service.getMetadata()).ifPresent(
            metadata -> idServiceMap.remove(metadata.getUid())
        );
    }

    protected void addPod(final V1Pod pod) {
        Optional.ofNullable(pod.getStatus()).ifPresent(
            status -> ipPodMap.put(status.getPodIP(), pod)
        );

        recompose();
    }

    protected void removePod(final V1Pod pod) {
        Optional.ofNullable(pod.getStatus()).ifPresent(
            status -> ipPodMap.remove(status.getPodIP())
        );
    }

    protected void addEndpoints(final V1Endpoints endpoints) {
        final String namespace = requireNonNull(endpoints.getMetadata()).getNamespace();
        final String name = requireNonNull(endpoints.getMetadata()).getName();

        requireNonNull(endpoints.getSubsets()).forEach(
            subset -> requireNonNull(subset.getAddresses()).forEach(
                address -> ipServiceMap.put(address.getIp(), namespace + ":" + name)
            )
        );

        recompose();
    }

    protected void removeEndpoints(final V1Endpoints endpoints) {
        requireNonNull(endpoints.getSubsets()).forEach(
            subset -> requireNonNull(subset.getAddresses()).forEach(
                address -> ipServiceMap.remove(address.getIp())
            )
        );
    }

    protected List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final Map<String, String> labels) {
        if (isNull(labels)) {
            return Collections.emptyList();
        }
        return labels.entrySet()
                     .stream()
                     .map(each -> new ServiceMetaInfo.KeyValue(each.getKey(), each.getValue()))
                     .collect(Collectors.toList());
    }

    protected ServiceMetaInfo findService(final String ip) {
        final ServiceMetaInfo service = ipServiceMetaInfoMap.get(ip);
        if (isNull(service)) {
            log.debug("Unknown ip {}, ip -> service is null", ip);
            return ServiceMetaInfo.UNKNOWN;
        }
        return service;
    }

    protected void recompose() {
        ipPodMap.forEach((ip, pod) -> {
            final String namespaceService = ipServiceMap.get(ip);
            final V1Service service;
            if (isNullOrEmpty(namespaceService) || isNull(service = idServiceMap.get(namespaceService))) {
                return;
            }

            final Map<String, Object> context = ImmutableMap.of("service", service, "pod", pod);
            final V1ObjectMeta podMetadata = requireNonNull(pod.getMetadata());

            ipServiceMetaInfoMap.computeIfAbsent(ip, unused -> {
                final ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();

                try {
                    serviceMetaInfo.setServiceName(serviceNameFormatter.format(context));
                } catch (Exception e) {
                    log.error("Failed to evaluate service name.", e);
                    serviceMetaInfo.setServiceName(requireNonNull(service.getMetadata()).getName());
                }
                serviceMetaInfo.setServiceInstanceName(
                    String.format("%s.%s", podMetadata.getName(), podMetadata.getNamespace()));
                serviceMetaInfo.setTags(transformLabelsToTags(podMetadata.getLabels()));

                return serviceMetaInfo;
            });
        });
    }

    protected boolean isEmpty() {
        return ipServiceMetaInfoMap.isEmpty();
    }
}
