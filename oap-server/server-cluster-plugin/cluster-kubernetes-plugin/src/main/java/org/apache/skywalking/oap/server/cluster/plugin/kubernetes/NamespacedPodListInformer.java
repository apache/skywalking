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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Slf4j
public enum NamespacedPodListInformer {

    /**
     * contains remote collector instances
     */
    INFORMER;

    private Lister<Pod> podLister;

    public synchronized void init(ClusterModuleKubernetesConfig podConfig, ResourceEventHandler<Pod> eventHandler) {
        try {
            final var kubernetesClient = new KubernetesClientBuilder().build();
            final var informer = kubernetesClient
                .pods()
                .inNamespace(podConfig.getNamespace())
                .withLabelSelector(podConfig.getLabelSelector())
                .inform(eventHandler);

            podLister = new Lister<>(informer.getIndexer());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                informer.close();
                kubernetesClient.close();
            }));
        } catch (Exception e) {
            log.error("cannot connect with api server in kubernetes", e);
        }
    }

    public Optional<List<Pod>> listPods() {
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
