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

package org.apache.skywalking.oap.server.health.checker.provider;

import com.google.common.util.concurrent.AtomicDouble;
import io.vavr.collection.Stream;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.health.checker.module.HealthCheckerModule;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;

import com.linecorp.armeria.common.HttpMethod;

/**
 * HealthCheckerProvider fetches health check metrics from telemetry module, then calculates health score and generates
 * details explains the score. External service or users can query health status by HealthCheckerService.
 */
@Slf4j
public class HealthCheckerProvider extends ModuleProvider {
    private final AtomicDouble score = new AtomicDouble();
    private final AtomicReference<String> details = new AtomicReference<>();
    private HealthCheckerConfig config;
    private MetricsCollector collector;
    private MetricsCreator metricsCreator;
    private ScheduledExecutorService ses;
    private HealthQueryService healthQueryService;

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return HealthCheckerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<HealthCheckerConfig>() {
            @Override
            public Class type() {
                return HealthCheckerConfig.class;
            }

            @Override
            public void onInitialized(final HealthCheckerConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        score.set(-1);
        ses = Executors.newSingleThreadScheduledExecutor();
        healthQueryService = new HealthQueryService(score, details);
        this.registerServiceImplementation(HealthQueryService.class, healthQueryService);
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        ModuleServiceHolder telemetry = getManager().find(TelemetryModule.NAME).provider();
        metricsCreator = telemetry.getService(MetricsCreator.class);
        collector = telemetry.getService(MetricsCollector.class);

        final var service = getManager().find(CoreModule.NAME)
                                        .provider()
                                        .getService(HTTPHandlerRegister.class);
        service.addHandler(new HealthCheckerHttpService(healthQueryService), Arrays.asList(HttpMethod.HEAD, HttpMethod.GET));
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        ses.scheduleAtFixedRate(() -> {
            StringBuilder unhealthyModules = new StringBuilder();
            AtomicBoolean hasUnhealthyModule = new AtomicBoolean(false);
            Stream.ofAll(collector.collect())
                                        .flatMap(metricFamily -> metricFamily.samples)
                                        .filter(sample -> metricsCreator.isHealthCheckerMetrics(sample.name))
                                        .forEach(sample -> {
                                            if (sample.value < 1) {
                                                unhealthyModules.append(metricsCreator.extractModuleName(sample.name)).append(",");
                                                hasUnhealthyModule.set(true);
                                            }
                                        });

            score.set(hasUnhealthyModule.get() ? 0 : 1);
            details.set(unhealthyModules.toString());
            },
            2, config.getCheckIntervalSeconds(), TimeUnit.SECONDS);
    }

    @Override public String[] requiredModules() {
        return new String[]{TelemetryModule.NAME};
    }
}
