/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.Optional;

public enum KubernetesPods {
    INSTANCE;

    private final LoadingCache<String, Optional<Pod>> podByIP;
    private final LoadingCache<ObjectID, Optional<Pod>> podByObjectID;

    @SneakyThrows
    KubernetesPods() {
        final CacheBuilder<Object, Object> cacheBuilder =
            CacheBuilder.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5));

        podByIP = cacheBuilder.build(new CacheLoader<>() {
            @Override
            public Optional<Pod> load(String ip) {
                try (final var kubernetesClient = new KubernetesClientBuilder().build()) {
                    return kubernetesClient
                            .pods()
                            .inAnyNamespace()
                            .withField("status.podIP", ip)
                            .list()
                            .getItems()
                            .stream()
                            .findFirst();
                }
            }
        });

        podByObjectID = cacheBuilder.build(new CacheLoader<>() {
            @Override
            public Optional<Pod> load(ObjectID objectID) {
                try (final var kubernetesClient = new KubernetesClientBuilder().build()) {
                    return Optional.ofNullable(
                            kubernetesClient
                                    .pods()
                                    .inNamespace(objectID.namespace())
                                    .withName(objectID.name())
                                    .get());
                }
            }
        });
    }

    @SneakyThrows
    public Optional<Pod> findByIP(final String ip) {
        return podByIP.get(ip);
    }

    @SneakyThrows
    public Optional<Pod> findByObjectID(final ObjectID id) {
        return podByObjectID.get(id);
    }
}
