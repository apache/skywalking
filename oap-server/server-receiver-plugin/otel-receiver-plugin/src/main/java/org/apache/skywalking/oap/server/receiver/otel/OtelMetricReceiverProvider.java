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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

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
    public ModuleConfig createConfigBeanIfAbsent() {
        config = new OtelMetricReceiverConfig();
        return config;
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
        final MeterSystem service = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
        Handler.all().stream()
            .filter(h -> config.getEnabledHandlers().contains(h.type()))
            .forEach(h -> h.active(config.getEnabledRulesFrom(h.type()), service, grpcHandlerRegister));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {SharingServerModule.NAME};
    }
}
