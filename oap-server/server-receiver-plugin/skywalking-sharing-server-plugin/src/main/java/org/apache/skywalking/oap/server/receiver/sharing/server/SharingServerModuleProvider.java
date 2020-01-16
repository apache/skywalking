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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.auth.AuthenticationInterceptor;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;

/**
 * @author peng-yongsheng, jian.tan
 */
public class SharingServerModuleProvider extends ModuleProvider {

    private final SharingServerConfig config;
    private GRPCServer grpcServer;
    private JettyServer jettyServer;
    private ReceiverGRPCHandlerRegister receiverGRPCHandlerRegister;
    private ReceiverJettyHandlerRegister receiverJettyHandlerRegister;

    public SharingServerModuleProvider() {
        super();
        this.config = new SharingServerConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return SharingServerModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() {
        if (config.getRestPort() != 0) {
            jettyServer = new JettyServer(Strings.isBlank(config.getRestHost()) ? "0.0.0.0" : config.getRestHost(), config.getRestPort(), config.getRestContextPath());
            jettyServer.initialize();

            this.registerServiceImplementation(JettyHandlerRegister.class, new JettyHandlerRegisterImpl(jettyServer));
        } else {
            this.receiverJettyHandlerRegister = new ReceiverJettyHandlerRegister();
            this.registerServiceImplementation(JettyHandlerRegister.class, receiverJettyHandlerRegister);
        }

        if (config.getGRPCPort() != 0) {
            grpcServer = new GRPCServer(Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(), config.getGRPCPort());
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

            this.registerServiceImplementation(GRPCHandlerRegister.class, new GRPCHandlerRegisterImpl(grpcServer));
        } else {
            this.receiverGRPCHandlerRegister = new ReceiverGRPCHandlerRegister();
            if (StringUtil.isNotEmpty(config.getAuthentication())) {
                receiverGRPCHandlerRegister.addFilter(new AuthenticationInterceptor(config.getAuthentication()));
            }
            this.registerServiceImplementation(GRPCHandlerRegister.class, receiverGRPCHandlerRegister);
        }
    }

    @Override public void start() {
        if (Objects.nonNull(grpcServer)) {
            grpcServer.addHandler(new HealthCheckServiceHandler());
        }

        if (Objects.nonNull(receiverGRPCHandlerRegister)) {
            receiverGRPCHandlerRegister.setGrpcHandlerRegister(getManager().find(CoreModule.NAME).provider().getService(GRPCHandlerRegister.class));
        }
        if (Objects.nonNull(receiverJettyHandlerRegister)) {
            receiverJettyHandlerRegister.setJettyHandlerRegister(getManager().find(CoreModule.NAME).provider().getService(JettyHandlerRegister.class));
        }
    }

    @Override public void notifyAfterCompleted() throws ModuleStartException {
        try {
            if (Objects.nonNull(grpcServer)) {
                grpcServer.start();
            }
            if (Objects.nonNull(jettyServer)) {
                jettyServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
