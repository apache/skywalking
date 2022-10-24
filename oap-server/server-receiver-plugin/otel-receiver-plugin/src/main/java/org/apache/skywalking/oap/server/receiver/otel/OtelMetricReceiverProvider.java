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

import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

import static java.util.stream.Collectors.toList;

public class OtelMetricReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private OtelMetricReceiverConfig config;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return OtelMetricReceiverModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<OtelMetricReceiverConfig>() {
            @Override
            public Class type() {
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
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (config.getEnabledHandlers().isEmpty()) {
            return;
        }
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
            .provider()
            .getService(GRPCHandlerRegister.class);
        final MeterSystem meterSystem = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
        final List<Handler> handlers =
            Handler.all().stream()
                .filter(h -> config.getEnabledHandlers().contains(h.type()))
                .collect(toList());
        for (Handler h : handlers) {
            h.active(config, meterSystem, grpcHandlerRegister);
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
