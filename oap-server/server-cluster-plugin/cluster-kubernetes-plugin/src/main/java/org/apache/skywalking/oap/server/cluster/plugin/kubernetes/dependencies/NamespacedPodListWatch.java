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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes.dependencies;

import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.cluster.plugin.kubernetes.Event;
import org.apache.skywalking.oap.server.cluster.plugin.kubernetes.ReusableWatch;

/**
 * Watch the api {@literal https://v1-9.docs.kubernetes.io/docs/reference/generated/kubernetes-api/v1.9/#watch-64}.
 *
 * @author gaohongtao
 */
public class NamespacedPodListWatch implements ReusableWatch<Event> {

    private final CoreV1Api api = new CoreV1Api();

    private final String namespace;

    private final String labelSelector;

    private final int watchTimeoutSeconds;

    private Watch<V1Pod> watch;

    public NamespacedPodListWatch(final String namespace, final String labelSelector, final int watchTimeoutSeconds) {
        this.namespace = namespace;
        this.labelSelector = labelSelector;
        this.watchTimeoutSeconds = watchTimeoutSeconds;
    }

    @Override public void initOrReset() {
        ApiClient client;
        try {
            client = Config.defaultClient();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        client.getHttpClient().setReadTimeout(watchTimeoutSeconds, TimeUnit.SECONDS);
        Configuration.setDefaultApiClient(client);
        try {
            watch = Watch.createWatch(
                client,
                api.listNamespacedPodCall(namespace, null, null, null,
                    null, labelSelector, Integer.MAX_VALUE,null,null, Boolean.TRUE,
                    null, null),
                new TypeToken<Watch.Response<V1Pod>>() { }.getType());
        } catch (ApiException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override public Iterator<Event> iterator() {
        return Iterators.transform(watch.iterator(), response -> {
            if (response == null) {
                throw new NullPointerException("Original event is null");
            }
            return new Event(response.type, response.object.getMetadata().getUid(), response.object.getStatus().getPodIP());
        });
    }
}
