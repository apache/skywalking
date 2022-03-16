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

package org.apache.skywalking.oap.server.receiver.zipkin;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.ZipkinSpanHTTPHandler;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class ZipkinReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private final ZipkinReceiverConfig config;
    private HTTPServer httpServer;

    public ZipkinReceiverProvider() {
        config = new ZipkinReceiverConfig();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ZipkinReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {

    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        HTTPServerConfig httpServerConfig = HTTPServerConfig.builder()
                                                            .host(config.getHost())
                                                            .port(config.getPort())
                                                            .contextPath(config.getContextPath())
                                                            .idleTimeOut(config.getIdleTimeOut())
                                                            .acceptorPriorityDelta(
                                                                   config.getAcceptorPriorityDelta())
                                                            .maxThreads(config.getMaxThreads())
                                                            .acceptQueueSize(config.getAcceptQueueSize())
                                                            .build();

        httpServer = new HTTPServer(httpServerConfig);
        httpServer.initialize();

        httpServer.addHandler(new ZipkinSpanHTTPHandler(config, getManager()));
    }

    @Override
    public void notifyAfterCompleted() {
        httpServer.start();
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME
        };
    }
}
