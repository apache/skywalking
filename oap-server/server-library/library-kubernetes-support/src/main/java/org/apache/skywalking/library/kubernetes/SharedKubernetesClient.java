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

package org.apache.skywalking.library.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

/**
 * Shared {@link KubernetesClient} singleton. All modules that need Kubernetes API
 * access should use this instead of creating their own client instances.
 *
 * <p>Each {@code KubernetesClient} spawns internal JDK {@code HttpClient} threads
 * (NIO selector, executor pool). Sharing a single client eliminates thread churn
 * from repeated client creation in Guava cache loaders.
 *
 * <p>Thread footprint (per JDK version):
 * <ul>
 *   <li>JDK 25+: 1 SelectorManager + virtual thread executor = ~1 platform thread</li>
 *   <li>JDK &lt; 25: 1 SelectorManager + 1 fixed executor thread = 2 platform threads</li>
 * </ul>
 */
public enum SharedKubernetesClient {
    INSTANCE;

    private final KubernetesClient client;

    SharedKubernetesClient() {
        client = new KubernetesClientBuilder()
            .withHttpClientFactory(new KubernetesHttpClientFactory())
            .build();
        Runtime.getRuntime().addShutdownHook(
            new Thread(client::close, "K8sClient-shutdown"));
    }

    public KubernetesClient get() {
        return client;
    }
}
