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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import lombok.SneakyThrows;

public enum KubernetesServices {
    INSTANCE;

    private final LoadingCache<KubernetesServices, List<V1Service>> services;
    private final LoadingCache<ObjectID, Optional<V1Service>> serviceByID;

    @SneakyThrows
    private KubernetesServices() {
        KubernetesClient.setDefault();

        final CoreV1Api coreV1Api = new CoreV1Api();

        final CacheBuilder<Object, Object> cacheBuilder =
            CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(3));

        services = cacheBuilder.build(CacheLoader.from(() -> {
            try {
                return coreV1Api
                    .listServiceForAllNamespaces(null, null, null, null, null, null, null, null,
                        null, null)
                    .getItems();
            } catch (ApiException e) {
                LoggerFactory.getLogger(getClass()).error("Failed to list Services.", e);
                return Collections.emptyList();
            }
        }));

        serviceByID = cacheBuilder.build(new CacheLoader<ObjectID, Optional<V1Service>>() {
            @Override
            public Optional<V1Service> load(ObjectID id) throws Exception {
                return Optional.ofNullable(
                    coreV1Api
                        .readNamespacedService(
                            id.name(),
                            id.namespace(),
                            null));
            }
        });
    }

    @SneakyThrows
    public List<V1Service> list() {
        return services.get(this);
    }

    @SneakyThrows
    public Optional<V1Service> findByID(final ObjectID id) {
        return serviceByID.get(id);
    }
}
