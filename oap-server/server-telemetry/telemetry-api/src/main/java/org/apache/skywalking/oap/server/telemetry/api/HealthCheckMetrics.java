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

package org.apache.skywalking.oap.server.telemetry.api;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

/**
 * HealthCheckMetrics intends to record health status.
 */
@Slf4j
public class HealthCheckMetrics implements HealthChecker {
    private final GaugeMetrics metrics;

    public HealthCheckMetrics(GaugeMetrics metrics) {
        this.metrics = metrics;
        // The initial status is unhealthy with -1 code.
        metrics.setValue(-1);
    }

    @Override
    public void health() {
        metrics.setValue(0);
    }

    @Override
    public void unHealth(Throwable t) {
        log.error("Health check fails", t);
        metrics.setValue(1);
    }

    @Override
    public void unHealth(String reason) {
        log.warn("Health check fails. reason: {}", reason);
        metrics.setValue(1);
    }
}
