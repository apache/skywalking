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

package org.apache.skywalking.oap.server.receiver.otel;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryLogHandler;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricHandler;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

import java.util.ArrayList;
import java.util.List;

public class OtelMetricReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";

    private List<Handler> handlers;

    private OtelMetricReceiverConfig config;

    private OpenTelemetryMetricRequestProcessor metricRequestProcessor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return OtelMetricReceiverModule.class;
    }

    @Override
    public ConfigCreator<OtelMetricReceiverConfig> newConfigCreator() {
        return new ConfigCreator<OtelMetricReceiverConfig>() {
            @Override
            public Class<OtelMetricReceiverConfig> type() {
                return OtelMetricReceiverConfig.class;
            }

            @Override
            public void onInitialized(final OtelMetricReceiverConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        metricRequestProcessor = new OpenTelemetryMetricRequestProcessor(
            getManager(), config);
        registerServiceImplementation(OpenTelemetryMetricRequestProcessor.class, metricRequestProcessor);
        final List<String> enabledHandlers = config.getEnabledHandlers();
        List<Handler> handlers = new ArrayList<>();

        final var openTelemetryMetricHandler = new OpenTelemetryMetricHandler(getManager(), metricRequestProcessor);
        if (enabledHandlers.contains(openTelemetryMetricHandler.type())) {
            handlers.add(openTelemetryMetricHandler);
        }
        final var openTelemetryLogHandler = new OpenTelemetryLogHandler(getManager());
        if (enabledHandlers.contains(openTelemetryLogHandler.type())) {
            handlers.add(openTelemetryLogHandler);
        }
        this.handlers = handlers;
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        metricRequestProcessor.start();
        for (Handler h : handlers) {
            h.active();
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {SharingServerModule.NAME};
    }
}
