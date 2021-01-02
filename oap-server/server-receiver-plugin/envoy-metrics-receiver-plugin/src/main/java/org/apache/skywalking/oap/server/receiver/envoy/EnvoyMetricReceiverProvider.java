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

package org.apache.skywalking.oap.server.receiver.envoy;

import org.apache.skywalking.aop.server.receiver.mesh.MeshReceiverModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.envoy.als.mx.FieldsHelper;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class EnvoyMetricReceiverProvider extends ModuleProvider {
    private final EnvoyMetricReceiverConfig config;

    protected String fieldMappingFile = "metadata-service-mapping.yaml";

    public EnvoyMetricReceiverProvider() {
        config = new EnvoyMetricReceiverConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return EnvoyMetricReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            FieldsHelper.SINGLETON.init(fieldMappingFile);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        GRPCHandlerRegister service = getManager().find(SharingServerModule.NAME)
                                                  .provider()
                                                  .getService(GRPCHandlerRegister.class);
        if (config.isAcceptMetricsService()) {
            final MetricServiceGRPCHandler handler = new MetricServiceGRPCHandler(getManager(), config);
            service.addHandler(handler);
            service.addHandler(new MetricServiceGRPCHandlerV3(handler));
        }
        final AccessLogServiceGRPCHandler handler = new AccessLogServiceGRPCHandler(getManager(), config);
        service.addHandler(handler);
        service.addHandler(new AccessLogServiceGRPCHandlerV3(handler));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME,
            SharingServerModule.NAME,
            MeshReceiverModule.NAME
        };
    }
}
