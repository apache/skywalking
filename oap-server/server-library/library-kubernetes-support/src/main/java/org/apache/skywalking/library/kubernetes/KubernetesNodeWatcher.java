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

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;

public enum KubernetesNodeWatcher implements ResourceEventHandler<V1Node> {
    INSTANCE;

    private final ExecutorService executor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("KubernetesNodeWatcher-%d")
            .setDaemon(true)
            .build()
    );
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<KubernetesNodeListener> listeners = new CopyOnWriteArraySet<>();

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        KubernetesClient.setDefault();

        final CoreV1Api coreV1Api = new CoreV1Api();
        final SharedInformerFactory factory = new SharedInformerFactory(executor);

        listenNodeEvents(coreV1Api, factory);

        factory.startAllRegisteredInformers();
    }

    public KubernetesNodeWatcher addListener(KubernetesNodeListener listener) {
        requireNonNull(listener, "listener");

        listeners.add(listener);

        return this;
    }

    @Override
    public void onAdd(V1Node node) {
        listeners.forEach(it -> it.onNodeAdded(node));
    }

    @Override
    public void onUpdate(V1Node oldNode, V1Node newNode) {
        listeners.forEach(it -> it.onNodeUpdated(oldNode, newNode));
    }

    @Override
    public void onDelete(V1Node node, boolean deletedFinalStateUnknown) {
        listeners.forEach(it -> it.onNodeDeleted(node));
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
                null,
                null,
                params.timeoutSeconds,
                params.watch,
                null
            ),
            V1Node.class,
            V1NodeList.class
        ).addEventHandler(this);
    }
}
