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
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;

public enum KubernetesServiceWatcher implements ResourceEventHandler<V1Service> {
    INSTANCE;

    private final ExecutorService executor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("KubernetesServiceWatcher-%d")
            .setDaemon(true)
            .build()
    );
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<KubernetesServiceListener> listeners = new CopyOnWriteArraySet<>();

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        KubernetesClient.setDefault();

        final CoreV1Api coreV1Api = new CoreV1Api();
        final SharedInformerFactory factory = new SharedInformerFactory(executor);

        listenServiceEvents(coreV1Api, factory);

        factory.startAllRegisteredInformers();
    }

    public KubernetesServiceWatcher addListener(KubernetesServiceListener listener) {
        requireNonNull(listener, "listener");

        listeners.add(listener);

        return this;
    }

    @Override
    public void onAdd(V1Service service) {
        listeners.forEach(it -> it.onServiceAdded(service));
    }

    @Override
    public void onUpdate(V1Service oldService, V1Service newService) {
        listeners.forEach(it -> it.onServiceUpdated(oldService, newService));
    }

    @Override
    public void onDelete(V1Service service, boolean deletedFinalStateUnknown) {
        listeners.forEach(it -> it.onServiceDeleted(service));
    }

    private void listenServiceEvents(final CoreV1Api coreV1Api,
                                  final SharedInformerFactory factory) {
        factory.sharedIndexInformerFor(
            params -> coreV1Api.listServiceForAllNamespacesCall(
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
            V1Service.class,
            V1ServiceList.class
        ).addEventHandler(this);
    }
}
