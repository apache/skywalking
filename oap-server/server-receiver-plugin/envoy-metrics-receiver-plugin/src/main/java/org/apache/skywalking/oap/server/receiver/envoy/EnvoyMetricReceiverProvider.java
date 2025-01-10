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

import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.aop.server.receiver.mesh.MeshReceiverModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.watermark.WatermarkGRPCInterceptor;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.util.FieldsHelper;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

import java.util.Objects;

public class EnvoyMetricReceiverProvider extends ModuleProvider {
    protected EnvoyMetricReceiverConfig config;
    protected GRPCServer grpcServer;
    protected GRPCHandlerRegister receiverGRPCHandlerRegister;

    protected String fieldMappingFile = "metadata-service-mapping.yaml";

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return EnvoyMetricReceiverModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<EnvoyMetricReceiverConfig>() {
            @Override
            public Class type() {
                return EnvoyMetricReceiverConfig.class;
            }

            @Override
            public void onInitialized(final EnvoyMetricReceiverConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            FieldsHelper.forClass(config.serviceMetaInfoFactory().clazz()).init(fieldMappingFile);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }

        if (config.getGRPCPort() != 0 && !RunningMode.isInitMode()) {
            if (config.isGRPCSslEnabled()) {
                grpcServer = new GRPCServer(
                    Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(),
                    config.getGRPCPort(),
                    config.getGRPCSslCertChainPath(),
                    config.getGRPCSslKeyPath(),
                    config.getGRPCSslTrustedCAsPath()
                );
            } else {
                grpcServer = new GRPCServer(
                    Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(),
                    config.getGRPCPort()
                );
            }
            if (config.getMaxMessageSize() > 0) {
                grpcServer.setMaxMessageSize(config.getMaxMessageSize());
            }
            if (config.getMaxConcurrentCallsPerConnection() > 0) {
                grpcServer.setMaxConcurrentCallsPerConnection(config.getMaxConcurrentCallsPerConnection());
            }
            if (config.getGRPCThreadPoolSize() > 0) {
                grpcServer.setThreadPoolSize(config.getGRPCThreadPoolSize());
            }
            grpcServer.initialize();

            this.receiverGRPCHandlerRegister = new GRPCHandlerRegisterImpl(grpcServer);
        }
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (!config.getAlsTCPAnalysis().isEmpty()) {
            getManager().find(CoreModule.NAME)
                        .provider()
                        .getService(OALEngineLoaderService.class)
                        .load(TCPOALDefine.INSTANCE);
        }

        if (config.isAcceptMetricsService()) {
            final MetricServiceGRPCHandler handler = new MetricServiceGRPCHandler(getManager(), config);
            // Always use the sharing gRPC server to accept metrics connections.
            final var service = getManager()
                .find(SharingServerModule.NAME)
                .provider()
                .getService(GRPCHandlerRegister.class);
            service.addHandler(handler);
            service.addHandler(new MetricServiceGRPCHandlerV3(handler));
        }

        final var service =
            Objects.nonNull(receiverGRPCHandlerRegister) ?
                receiverGRPCHandlerRegister :
                getManager()
                    .find(SharingServerModule.NAME)
                    .provider()
                    .getService(GRPCHandlerRegister.class);
        final var handler = new AccessLogServiceGRPCHandler(getManager(), config);
        service.addHandler(handler);
        service.addHandler(new AccessLogServiceGRPCHandlerV3(handler));
        service.addHandler(new SatelliteAccessLogServiceGRPCHandlerV3(handler));
        service.addFilter(WatermarkGRPCInterceptor.INSTANCE);
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        try {
            if (Objects.nonNull(grpcServer) && !RunningMode.isInitMode()) {
                grpcServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
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
