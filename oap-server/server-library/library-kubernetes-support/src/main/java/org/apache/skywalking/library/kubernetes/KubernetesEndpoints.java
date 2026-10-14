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

import io.fabric8.kubernetes.api.model.Endpoints;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Reads the endpoints of the cluster on the calling thread, which is the background reader of a {@link LookupCache},
 * once they are {@link CachedRead#LIST_REFRESH_PERIOD} old.
 */
public enum KubernetesEndpoints {
    INSTANCE;

    private final CachedRead<List<Endpoints>> endpoints = new CachedRead<>(
        () -> KubernetesApiReads.read(
            "list endpoints",
            () -> KubernetesApiReads.client()
                                    .endpoints()
                                    .inAnyNamespace()
                                    .list()
                                    .getItems()
        ),
        Collections.emptyList()
    );

    /**
     * @throws KubernetesReadFailedException when the endpoints could not be read
     */
    public List<Endpoints> list() {
        return endpoints.get(CachedRead.LIST_REFRESH_PERIOD);
    }

    /**
     * @param maxAge how long ago the endpoints may have been read, such as shorter than usual when a pod's IP is not
     *               in them, as the pod may be new
     * @throws KubernetesReadFailedException when the endpoints could not be read
     */
    public List<Endpoints> list(final Duration maxAge) {
        return endpoints.get(maxAge);
    }
}
