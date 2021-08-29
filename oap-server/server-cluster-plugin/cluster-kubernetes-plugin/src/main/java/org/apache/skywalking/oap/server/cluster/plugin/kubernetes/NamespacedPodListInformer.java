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

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.isNull;

@Slf4j
public enum NamespacedPodListInformer {

    /**
     * contains remote collector instances
     */
    INFORMER;

    private Lister<V1Pod> podLister;

    private SharedInformerFactory factory;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SKYWALKING_KUBERNETES_CLUSTER_INFORMER");
        thread.setDaemon(true);
        return thread;
    });

    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (Objects.nonNull(factory)) {
                factory.stopAllRegisteredInformers();
            }
        }));
    }

    public synchronized void init(ClusterModuleKubernetesConfig podConfig) {

        try {
            doStartPodInformer(podConfig);
        } catch (IOException e) {
            log.error("cannot connect with api server in kubernetes", e);
        }
    }

    private void doStartPodInformer(ClusterModuleKubernetesConfig podConfig) throws IOException {

        ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build());
        Configuration.setDefaultApiClient(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        factory = new SharedInformerFactory(executorService);

        SharedIndexInformer<V1Pod> podSharedIndexInformer = factory.sharedIndexInformerFor(
            params -> coreV1Api.listNamespacedPodCall(
                podConfig.getNamespace(), null, null, null, null,
                podConfig.getLabelSelector(), Integer.MAX_VALUE, params.resourceVersion, null, params.timeoutSeconds,
                params.watch, null
            ),
            V1Pod.class, V1PodList.class
        );

        factory.startAllRegisteredInformers();
        podLister = new Lister<>(podSharedIndexInformer.getIndexer());
    }

    public Optional<List<V1Pod>> listPods() {
        if (isNull(podLister)) {
            return Optional.empty();
        }
        return Optional.ofNullable(podLister.list().size() != 0
                                       ? podLister.list()
                                                  .stream()
                                                  .filter(
                                                      item -> "Running".equalsIgnoreCase(item.getStatus().getPhase()))
                                                  .collect(Collectors.toList())
                                       : null);

    }

}
