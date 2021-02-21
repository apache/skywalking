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

package org.apache.skywalking.oap.server.configuration.configmap;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurationConfigmapInformer {

    private Lister<V1ConfigMap> configMapLister;

    private SharedInformerFactory factory;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SKYWALKING_KUBERNETES_CONFIGURATION_INFORMER");
        thread.setDaemon(true);
        return thread;
    });

    public ConfigurationConfigmapInformer(ConfigmapConfigurationSettings settings) {
        try {
            doStartConfigMapInformer(settings);
            doAddShutdownHook();
        } catch (IOException e) {
            log.error("cannot connect with api server in kubernetes", e);
        }
    }

    private void doAddShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (Objects.nonNull(factory)) {
                factory.stopAllRegisteredInformers();
            }
        }));
    }

    private void doStartConfigMapInformer(final ConfigmapConfigurationSettings settings) throws IOException {
        ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build());
        Configuration.setDefaultApiClient(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        factory = new SharedInformerFactory(executorService);

        SharedIndexInformer<V1ConfigMap> configMapSharedIndexInformer = factory.sharedIndexInformerFor(
            params -> coreV1Api.listNamespacedConfigMapCall(
                settings.getNamespace(), null, null, null, null, settings.getLabelSelector()
                , 1, params.resourceVersion, 300, params.watch, null
            ),
            V1ConfigMap.class, V1ConfigMapList.class
        );

        factory.startAllRegisteredInformers();
        configMapLister = new Lister<>(configMapSharedIndexInformer.getIndexer());
    }

    public Optional<V1ConfigMap> configMap() {
        return Optional.ofNullable(configMapLister.list().size() == 1 ? configMapLister.list().get(0) : null);
    }

}
