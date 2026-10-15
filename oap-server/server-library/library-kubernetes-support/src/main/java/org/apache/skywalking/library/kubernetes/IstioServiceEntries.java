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

import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.client.IstioClient;

import java.util.Collections;
import java.util.List;

/**
 * Reads the Istio ServiceEntries of the cluster on the calling thread, which is the background reader of a
 * {@link LookupCache}, once they are {@link CachedRead#LIST_REFRESH_PERIOD} old.
 */
public enum IstioServiceEntries {
    INSTANCE;

    private final CachedRead<List<ServiceEntry>> serviceEntries = new CachedRead<>(
        () -> KubernetesApiReads.read(
            "list serviceentries",
            () -> KubernetesApiReads.client()
                                    .adapt(IstioClient.class)
                                    .v1beta1()
                                    .serviceEntries()
                                    .inAnyNamespace()
                                    .list()
                                    .getItems()
        ),
        Collections.emptyList()
    );

    /**
     * @return the ServiceEntries, none when the API server does not serve them, as Istio is not installed
     * @throws KubernetesReadFailedException when the ServiceEntries could not be read
     */
    public List<ServiceEntry> list() {
        return serviceEntries.get(CachedRead.LIST_REFRESH_PERIOD);
    }
}
