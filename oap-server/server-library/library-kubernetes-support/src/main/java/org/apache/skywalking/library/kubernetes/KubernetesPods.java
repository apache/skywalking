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

import io.fabric8.kubernetes.api.model.Pod;

import java.util.List;
import java.util.Optional;

/**
 * Reads pods from the API server on the calling thread, which is the background reader of a {@link LookupCache}.
 */
public enum KubernetesPods {
    INSTANCE;

    /**
     * @throws KubernetesReadFailedException when the pods could not be read
     */
    public Optional<Pod> findByIP(final String ip) {
        return Optional.ofNullable(KubernetesApiReads.read(
            "list pods",
            () -> choosePod(KubernetesApiReads.client()
                                              .pods()
                                              .inAnyNamespace()
                                              .withField("status.podIP", ip)
                                              .list()
                                              .getItems()).orElse(null)
        ));
    }

    /**
     * @throws KubernetesReadFailedException when the pod could not be read
     */
    public Optional<Pod> findByObjectID(final ObjectID id) {
        return Optional.ofNullable(KubernetesApiReads.read(
            "get pods",
            () -> KubernetesApiReads.client()
                                    .pods()
                                    .inNamespace(id.namespace())
                                    .withName(id.name())
                                    .get()
        ));
    }

    /**
     * A completed pod keeps its IP after the IP is given to a new pod, so the pod that has not terminated is chosen.
     */
    static Optional<Pod> choosePod(final List<Pod> podsUsingSameIP) {
        return podsUsingSameIP.stream()
                              .filter(pod -> !isTerminated(pod))
                              .findFirst()
                              .or(() -> podsUsingSameIP.stream().findFirst());
    }

    private static boolean isTerminated(final Pod pod) {
        final String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        return "Succeeded".equals(phase) || "Failed".equals(phase);
    }
}
