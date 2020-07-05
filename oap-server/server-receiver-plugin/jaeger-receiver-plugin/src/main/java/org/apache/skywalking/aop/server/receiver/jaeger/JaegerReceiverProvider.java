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

package org.apache.skywalking.aop.server.receiver.jaeger;

import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class JaegerReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private JaegerReceiverConfig config;
    private GRPCServer grpcServer = null;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return JaegerReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        config = new JaegerReceiverConfig();
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (config.getGRPCPort() > 0) {
            grpcServer = new GRPCServer(Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(), config
                .getGRPCPort());
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
        }
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        SourceReceiver sourceReceiver = getManager().find(CoreModule.NAME).provider().getService(SourceReceiver.class);

        if (Objects.nonNull(grpcServer)) {
            grpcServer.addHandler(new JaegerGRPCHandler(sourceReceiver, config));
        } else {
            GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                                  .provider()
                                                                  .getService(GRPCHandlerRegister.class);
            grpcHandlerRegister.addHandler(new JaegerGRPCHandler(sourceReceiver, config));
        }

    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        try {
            if (Objects.nonNull(grpcServer)) {
                grpcServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {SharingServerModule.NAME};
    }
}
