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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.auth.AuthenticationInterceptor;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class SharingServerModuleProvider extends ModuleProvider {

    private SharingServerConfig config;
    private GRPCServer grpcServer;
    private HTTPServer httpServer;
    private ReceiverGRPCHandlerRegister receiverGRPCHandlerRegister;
    private ReceiverHTTPHandlerRegister receiverHTTPHandlerRegister;
    private AuthenticationInterceptor authenticationInterceptor;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return SharingServerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<SharingServerConfig>() {
            @Override
            public Class type() {
                return SharingServerConfig.class;
            }

            @Override
            public void onInitialized(final SharingServerConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() {
        if (config.getRestPort() > 0) {
            HTTPServerConfig httpServerConfig =
                HTTPServerConfig.builder()
                                .host(config.getRestHost()).port(config.getRestPort())
                                .contextPath(config.getRestContextPath())
                                .maxThreads(config.getRestMaxThreads())
                                .acceptQueueSize(config.getRestAcceptQueueSize())
                                .idleTimeOut(config.getRestIdleTimeOut())
                                .maxRequestHeaderSize(config.getHttpMaxRequestHeaderSize()).build();
            httpServerConfig.setHost(Strings.isBlank(config.getRestHost()) ? "0.0.0.0" : config.getRestHost());
            httpServerConfig.setPort(config.getRestPort());
            httpServerConfig.setContextPath(config.getRestContextPath());

            setBootingParameter("oap.external.http.host", config.getRestHost());
            setBootingParameter("oap.external.http.port", config.getRestPort());

            httpServer = new HTTPServer(httpServerConfig);
            httpServer.initialize();

            this.registerServiceImplementation(HTTPHandlerRegister.class, new HTTPHandlerRegisterImpl(httpServer));
        } else {
            this.receiverHTTPHandlerRegister = new ReceiverHTTPHandlerRegister();
            this.registerServiceImplementation(HTTPHandlerRegister.class, receiverHTTPHandlerRegister);
        }

        if (StringUtil.isNotEmpty(config.getAuthentication())) {
            authenticationInterceptor = new AuthenticationInterceptor(config.getAuthentication());
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
            setBootingParameter("oap.external.grpc.host", config.getGRPCHost());
            setBootingParameter("oap.external.grpc.port", config.getGRPCPort());
            if (config.getMaxMessageSize() > 0) {
                grpcServer.setMaxMessageSize(config.getMaxMessageSize());
            }
            if (config.getMaxConcurrentCallsPerConnection() > 0) {
                grpcServer.setMaxConcurrentCallsPerConnection(config.getMaxConcurrentCallsPerConnection());
            }
            if (config.getGRPCThreadPoolQueueSize() > 0) {
                grpcServer.setThreadPoolQueueSize(config.getGRPCThreadPoolQueueSize());
            }
            if (config.getGRPCThreadPoolSize() > 0) {
                grpcServer.setThreadPoolSize(config.getGRPCThreadPoolSize());
            }
            grpcServer.initialize();

            GRPCHandlerRegisterImpl grpcHandlerRegister = new GRPCHandlerRegisterImpl(grpcServer);
            if (Objects.nonNull(authenticationInterceptor)) {
                grpcHandlerRegister.addFilter(authenticationInterceptor);
            }
            this.registerServiceImplementation(GRPCHandlerRegister.class, grpcHandlerRegister);
        } else {
            this.receiverGRPCHandlerRegister = new ReceiverGRPCHandlerRegister();
            if (Objects.nonNull(authenticationInterceptor)) {
                receiverGRPCHandlerRegister.addFilter(authenticationInterceptor);
            }
            this.registerServiceImplementation(GRPCHandlerRegister.class, receiverGRPCHandlerRegister);
        }
    }

    @Override
    public void start() {
        if (Objects.nonNull(grpcServer)) {
            grpcServer.addHandler(new HealthCheckServiceHandler());
        }

        if (Objects.nonNull(receiverGRPCHandlerRegister)) {
            receiverGRPCHandlerRegister.setGrpcHandlerRegister(getManager().find(CoreModule.NAME)
                                                                           .provider()
                                                                           .getService(GRPCHandlerRegister.class));
        }
        if (Objects.nonNull(receiverHTTPHandlerRegister)) {
            receiverHTTPHandlerRegister.setHttpHandlerRegister(getManager().find(CoreModule.NAME)
                                                                           .provider()
                                                                           .getService(HTTPHandlerRegister.class));
        }
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        try {
            if (Objects.nonNull(grpcServer) && !RunningMode.isInitMode()) {
                grpcServer.start();
            }
            if (Objects.nonNull(httpServer) && !RunningMode.isInitMode()) {
                httpServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
