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

import io.fabric8.kubernetes.client.jdkhttp.JdkHttpClientFactory;
import org.apache.skywalking.oap.server.library.util.VirtualThreads;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom {@link JdkHttpClientFactory} that configures the JDK {@link HttpClient}
 * with a minimal executor to reduce thread usage.
 *
 * <ul>
 *   <li>JDK 25+: virtual-thread-per-task executor (0 platform threads)</li>
 *   <li>JDK &lt; 25: single-thread fixed pool named {@code K8sClient-executor-0}</li>
 * </ul>
 *
 * <p>The JDK {@code HttpClient} always creates 1 internal {@code SelectorManager}
 * thread regardless. This factory controls only the executor threads.
 */
final class KubernetesHttpClientFactory extends JdkHttpClientFactory {

    @Override
    protected void additionalConfig(final HttpClient.Builder builder) {
        final ExecutorService executor = VirtualThreads.createExecutor(
            "K8sClient-executor",
            () -> Executors.newFixedThreadPool(1, r -> {
                final Thread t = new Thread(r, "K8sClient-executor-0");
                t.setDaemon(true);
                return t;
            })
        );
        builder.executor(executor);
    }
}
