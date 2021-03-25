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

package org.apache.skywalking.oap.meter.analyzer.k8s;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

@Slf4j
public class K8sInfoRegistry {

    private final static K8sInfoRegistry INSTANCE = new K8sInfoRegistry();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final Map<String/* ip */, V1Pod> ipPodMap = new ConcurrentHashMap<>();
    private final Map<String/* ip */, String/* serviceName.namespace */> ipServiceMap = new ConcurrentHashMap<>();
    private final Map<String/* podName */, String /* serviceName.namespace */> podServiceMap = new ConcurrentHashMap<>();
    private ExecutorService executor;

    public static K8sInfoRegistry getInstance() {
        return INSTANCE;
    }

    private void init() {
        executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("K8sInfoRegistry-%d")
                .setDaemon(true)
                .build()
        );
    }

    @SneakyThrows
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            init();
            final ApiClient apiClient = Config.defaultClient();
            apiClient.setHttpClient(apiClient.getHttpClient()
                                             .newBuilder()
                                             .readTimeout(0, TimeUnit.SECONDS)
                                             .build());
            Configuration.setDefaultApiClient(apiClient);

            final CoreV1Api coreV1Api = new CoreV1Api();
            final SharedInformerFactory factory = new SharedInformerFactory(executor);

            listenEndpointsEvents(coreV1Api, factory);
            listenPodEvents(coreV1Api, factory);
            factory.startAllRegisteredInformers();
        }
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

    private void addPod(final V1Pod pod) {
        ofNullable(pod.getStatus()).ifPresent(
            status -> ofNullable(status.getPodIP()).ifPresent(
                ip -> ipPodMap.put(ip, pod))
        );

        recompose();
    }

    private void removePod(final V1Pod pod) {
        ofNullable(pod.getStatus()).ifPresent(
            status -> ipPodMap.remove(status.getPodIP())
        );
        ofNullable(pod.getMetadata()).ifPresent(
            metadata -> podServiceMap.remove(pod.getMetadata().getName())
        );
    }

    private void addEndpoints(final V1Endpoints endpoints) {
        V1ObjectMeta endpointsMetadata = endpoints.getMetadata();
        if (isNull(endpointsMetadata)) {
            log.error("Endpoints metadata is null: {}", endpoints);
            return;
        }

        final String namespace = endpointsMetadata.getNamespace();
        final String name = endpointsMetadata.getName();

        ofNullable(endpoints.getSubsets()).ifPresent(subsets -> subsets.forEach(
            subset -> ofNullable(subset.getAddresses()).ifPresent(addresses -> addresses.forEach(
                address -> ipServiceMap.put(address.getIp(), name + "." + namespace)
            ))
        ));

        recompose();
    }

    private void removeEndpoints(final V1Endpoints endpoints) {
        ofNullable(endpoints.getSubsets()).ifPresent(subsets -> subsets.forEach(
            subset -> ofNullable(subset.getAddresses()).ifPresent(addresses -> addresses.forEach(
                address -> ipServiceMap.remove(address.getIp())
            ))
        ));
        recompose();
    }

    private void recompose() {
        ipPodMap.forEach((ip, pod) -> {
            final String namespaceService = ipServiceMap.get(ip);
            if (isNullOrEmpty(namespaceService)) {
                podServiceMap.remove(ip);
                return;
            }

            final V1ObjectMeta podMetadata = pod.getMetadata();
            if (isNull(podMetadata)) {
                log.warn("Pod metadata is null, {}", pod);
                return;
            }

            podServiceMap.put(pod.getMetadata().getName(), namespaceService);
        });
    }

    public String findServiceName(String podName) {
        return this.podServiceMap.get(podName);
    }
}
