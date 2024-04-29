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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ConfigurationConfigmapInformer {
    private final Lister<ConfigMap> configMapLister;

    public ConfigurationConfigmapInformer(ConfigmapConfigurationSettings settings) {
        final var client = new KubernetesClientBuilder().build();
        final var informer = client
            .configMaps()
            .inNamespace(settings.getNamespace())
            .withLabelSelector(settings.getLabelSelector())
            .inform();

        configMapLister = new Lister<>(informer.getIndexer());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            informer.stop();
            client.close();
        }));
    }

    public Map<String, String> configMapData() {
        Map<String, String> configMapData = new HashMap<>();
        if (configMapLister != null) {
            final List<ConfigMap> list = configMapLister.list();
            if (list != null) {
                list.forEach(cf -> {
                    Map<String, String> data = cf.getData();
                    if (data == null) {
                        return;
                    }
                    configMapData.putAll(data);
                });
            }
        }

        return configMapData;
    }
}
