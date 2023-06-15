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
import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.client.DefaultIstioClient;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public enum IstioServiceEntries {
    INSTANCE;

    private final LoadingCache<IstioServiceEntries, List<ServiceEntry>> serviceEntries;

    @SneakyThrows
    IstioServiceEntries() {
        final CacheBuilder<Object, Object> cacheBuilder =
            CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(3));

        serviceEntries = cacheBuilder.build(CacheLoader.from(() -> {
            try (final var istioClient = new DefaultIstioClient()) {
                return istioClient
                    .v1beta1()
                    .serviceEntries()
                    .inAnyNamespace()
                    .list()
                    .getItems();
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).error("Failed to list Endpoints.", e);
                return Collections.emptyList();
            }
        }));
    }

    @SneakyThrows
    public List<ServiceEntry> list() {
        return serviceEntries.get(this);
    }
}
