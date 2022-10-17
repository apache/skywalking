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

import java.time.Duration;
import java.util.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import lombok.SneakyThrows;

public enum KubernetesPods {
    INSTANCE;

    private static final String FIELD_SELECTOR_PATTERN_POD_IP = "status.podIP=%s";

    private final LoadingCache<String, Optional<V1Pod>> podByIP;
    private final LoadingCache<ObjectID, Optional<V1Pod>> podByObjectID;

    @SneakyThrows
    private KubernetesPods() {
        KubernetesClient.setDefault();

        final CoreV1Api coreV1Api = new CoreV1Api();
        final CacheBuilder<Object, Object> cacheBuilder =
            CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5));

        podByIP = cacheBuilder.build(new CacheLoader<String, Optional<V1Pod>>() {
            @Override
            public Optional<V1Pod> load(String ip) throws Exception {
                return coreV1Api
                    .listPodForAllNamespaces(
                        null, null, String.format(FIELD_SELECTOR_PATTERN_POD_IP, ip),
                        null, null, null, null, null, null, null)
                    .getItems()
                    .stream()
                    .findFirst();
            }
        });

        podByObjectID = cacheBuilder.build(new CacheLoader<ObjectID, Optional<V1Pod>>() {
            @Override
            public Optional<V1Pod> load(ObjectID objectID) throws Exception {
                return Optional.ofNullable(
                    coreV1Api
                        .readNamespacedPod(
                            objectID.name(),
                            objectID.namespace(),
                            null));
            }
        });
    }

    @SneakyThrows
    public Optional<V1Pod> findByIP(final String ip) {
        return podByIP.get(ip);
    }

    @SneakyThrows
    public Optional<V1Pod> findByObjectID(final ObjectID id) {
        return podByObjectID.get(id);
    }
}
