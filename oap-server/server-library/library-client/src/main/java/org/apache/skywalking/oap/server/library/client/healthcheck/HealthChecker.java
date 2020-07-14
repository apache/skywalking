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

package org.apache.skywalking.oap.server.library.client.healthcheck;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HealthChecker could provide health status to the listener.
 */
@Slf4j
@RequiredArgsConstructor
public class HealthChecker {
    public static final HealthChecker DEFAULT_CHECKER = new HealthChecker(health -> { });

    private final HealthListener listener;

    /**
     * Invoking when service is healthy.
     */
    public void health() {
        listener.listen(true);
    }

    /**
     * Invoking when service is unhealthy.
     * @param t the reason of unhealthy status.
     */
    public void unHealth(Throwable t) {
        log.error("Elasticsearch health check is failed", t);
        listener.listen(false);
    }
}
