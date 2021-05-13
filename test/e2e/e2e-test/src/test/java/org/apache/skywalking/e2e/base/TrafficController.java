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

package org.apache.skywalking.e2e.base;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public final class TrafficController {
    @Builder.Default
    private final int interval = 1000;
    @Builder.Default
    private final boolean logResult = true;
    private final String host;
    private final Callable<?> sender;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10, r -> {
        final Thread thread = new Thread(r, "traffic-controller");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> future;

    public TrafficController start() {
        if (future == null) {
            future = executor.scheduleAtFixedRate(() -> {
                try {
                    final Object result = sender.call();
                    if (logResult) {
                        LOGGER.info("response: {}", result);
                    }
                } catch (final Exception e) {
                    LOGGER.error("failed to send traffic", e);
                }
            }, 0, interval, TimeUnit.MILLISECONDS);
        }

        return this;
    }

    public void stop() {
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }
}
