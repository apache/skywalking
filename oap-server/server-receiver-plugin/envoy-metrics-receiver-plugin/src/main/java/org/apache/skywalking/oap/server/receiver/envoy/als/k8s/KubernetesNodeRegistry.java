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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeAddress;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1NodeStatus;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;

@Slf4j
final class KubernetesNodeRegistry implements ResourceEventHandler<V1Node> {
    private final Set<String> nodeIPs;

    private final ExecutorService executor;

    public KubernetesNodeRegistry() {
        nodeIPs = Collections.newSetFromMap(new ConcurrentHashMap<>());
        executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("KubernetesNodeRegistry-%d")
                .setDaemon(true)
                .build()
        );
    }

    public void start() throws IOException {
        final ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(apiClient.getHttpClient()
                                         .newBuilder()
                                         .readTimeout(0, TimeUnit.SECONDS)
                                         .build());
        Configuration.setDefaultApiClient(apiClient);

        final CoreV1Api coreV1Api = new CoreV1Api();
        final SharedInformerFactory factory = new SharedInformerFactory(executor);

        listenNodeEvents(coreV1Api, factory);

        factory.startAllRegisteredInformers();
    }

    private void listenNodeEvents(final CoreV1Api coreV1Api,
                                  final SharedInformerFactory factory) {
        factory.sharedIndexInformerFor(
            params -> coreV1Api.listNodeCall(
                null,
                null,
                null,
                null,
                null,
                null,
                params.resourceVersion,
                null,
                params.timeoutSeconds,
                params.watch,
                null
            ),
            V1Node.class,
            V1NodeList.class
        ).addEventHandler(this);
    }

    @Override
    public void onAdd(final V1Node node) {
        forEachAddress(node, nodeIPs::add);
    }

    @Override
    public void onUpdate(final V1Node oldNode, final V1Node newNode) {
        onAdd(newNode);
    }

    @Override
    public void onDelete(final V1Node node,
                         final boolean deletedFinalStateUnknown) {
        forEachAddress(node, nodeIPs::remove);
    }

    void forEachAddress(final V1Node node,
                        final Consumer<String> consume) {
        Optional.ofNullable(node)
                .map(V1Node::getStatus)
                .map(V1NodeStatus::getAddresses)
                .ifPresent(addresses ->
                               addresses.stream()
                                        .map(V1NodeAddress::getAddress)
                                        .filter(StringUtil::isNotBlank)
                                        .forEach(consume)
                );
    }

    boolean isNode(final String ip) {
        return nodeIPs.contains(ip);
    }
}
