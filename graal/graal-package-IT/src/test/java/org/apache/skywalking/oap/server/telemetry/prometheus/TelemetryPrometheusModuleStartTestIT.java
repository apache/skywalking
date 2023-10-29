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

package org.apache.skywalking.oap.server.telemetry.prometheus;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class TelemetryPrometheusModuleStartTestIT {

    @Container
    public final GenericContainer<?> container =
            new GenericContainer<>("prom/prometheus:v2.30.0")
                    .withExposedPorts(9090)
                    .waitingFor(Wait.forHttp("/-/healthy").forPort(9090).forStatusCode(200));

    @Test
    public void moduleStart() throws ModuleStartException {
        PrometheusTelemetryProvider prometheusTelemetryProvider = new PrometheusTelemetryProvider();
        PrometheusConfig config = new PrometheusConfig();
        config.setHost(container.getHost());
        config.setPort(container.getMappedPort(9090));
        prometheusTelemetryProvider.newConfigCreator().onInitialized(config);

        prometheusTelemetryProvider.prepare();
        prometheusTelemetryProvider.start();
    }
}
