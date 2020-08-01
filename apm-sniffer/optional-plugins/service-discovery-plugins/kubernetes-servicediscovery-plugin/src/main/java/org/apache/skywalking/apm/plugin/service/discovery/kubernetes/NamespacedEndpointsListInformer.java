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

package org.apache.skywalking.apm.plugin.service.discovery.kubernetes;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import static org.apache.skywalking.apm.plugin.service.discovery.kubernetes.KubernetesServiceDiscoveryPluginConfig.Plugin.KubernetesService.LABEL_SELECTOR;
import static org.apache.skywalking.apm.plugin.service.discovery.kubernetes.KubernetesServiceDiscoveryPluginConfig.Plugin.KubernetesService.NAMESPACE;

public class NamespacedEndpointsListInformer {

    private static final ILog LOG = LogManager.getLogger(NamespacedEndpointsListInformer.class);

    private Lister<V1Endpoints> endpointsLister;

    private SharedInformerFactory factory;

    void boot() {
        try {
            doStartPodInformer(NAMESPACE, LABEL_SELECTOR);
        } catch (IOException e) {
            LOG.error("cannot connect with api server in kubernetes", e);
        }
    }

    void shutdown() {
        factory.stopAllRegisteredInformers();
    }

    Optional<V1EndpointSubset> getV1EndpointSubset() {
        List<V1Endpoints> endpointsList = endpointsLister.list();
        List<V1EndpointSubset> subsets = endpointsList.size() == 1 ? endpointsList.get(0).getSubsets() : null;
        return Objects.nonNull(subsets) && subsets.size() == 1 ? Optional.of(subsets.get(0)) : Optional.empty();
    }

    private void doStartPodInformer(final String namespace, final String labelSelector) throws IOException {
        ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build());
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        factory = new SharedInformerFactory(Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("KUBERNETES SERVICE DISCOVERY THREAD");
            return thread;
        }));
        SharedIndexInformer<V1Endpoints> endpointsSharedIndexInformer = factory.sharedIndexInformerFor(
            params -> coreV1Api.listNamespacedEndpointsCall(
                namespace, null, null, null, null,
                labelSelector, Integer.MAX_VALUE, params.resourceVersion, params.timeoutSeconds,
                params.watch, null
            ),
            V1Endpoints.class, V1EndpointsList.class
        );
        factory.startAllRegisteredInformers();
        endpointsLister = new Lister<>(endpointsSharedIndexInformer.getIndexer());
    }
}
