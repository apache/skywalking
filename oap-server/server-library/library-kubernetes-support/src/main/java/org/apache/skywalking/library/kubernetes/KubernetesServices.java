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

import io.fabric8.kubernetes.api.model.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Reads services from the API server on the calling thread, which is the background reader of a
 * {@link LookupCache}; the list of all services once it is {@link CachedRead#LIST_REFRESH_PERIOD} old.
 */
public enum KubernetesServices {
    INSTANCE;

    private final CachedRead<List<Service>> services = new CachedRead<>(
        () -> KubernetesApiReads.read(
            "list services",
            () -> KubernetesApiReads.client()
                                    .services()
                                    .inAnyNamespace()
                                    .list()
                                    .getItems()
        ),
        Collections.emptyList()
    );

    /**
     * @throws KubernetesReadFailedException when the list could not be read
     */
    public List<Service> list() {
        return services.get(CachedRead.LIST_REFRESH_PERIOD);
    }

    /**
     * @param maxAge how long ago the list may have been read, such as shorter than usual when a pod's service is not
     *               in it, as the service may be new
     * @throws KubernetesReadFailedException when the list could not be read
     */
    public List<Service> list(final Duration maxAge) {
        return services.get(maxAge);
    }

    /**
     * @throws KubernetesReadFailedException when the service could not be read
     */
    public Optional<Service> findByID(final ObjectID id) {
        return Optional.ofNullable(KubernetesApiReads.read(
            "get services",
            () -> KubernetesApiReads.client()
                                    .services()
                                    .inNamespace(id.namespace())
                                    .withName(id.name())
                                    .get()
        ));
    }
}
