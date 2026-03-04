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

package org.apache.skywalking.oap.query.traceql;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.query.traceql.handler.SkyWalkingTraceQLApiHandler;
import org.apache.skywalking.oap.query.traceql.handler.ZipkinTraceQLApiHandler;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;

public class TraceQLProvider extends ModuleProvider {
    public static final String NAME = "default";
    private TraceQLConfig config;
    private HTTPServer httpServer;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return TraceQLModule.class;
    }

    @Override
    public ConfigCreator<TraceQLConfig> newConfigCreator() {
        return new ConfigCreator<>() {
            @Override
            public Class<TraceQLConfig> type() {
                return TraceQLConfig.class;
            }

            @Override
            public void onInitialized(final TraceQLConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {

    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        HTTPServerConfig httpServerConfig = HTTPServerConfig.builder()
                                                            .host(config.getRestHost())
                                                            .port(config.getRestPort())
                                                            .contextPath(
                                                                "/") // Base context path for the server, individual handlers will have their own context paths
                                                            .idleTimeOut(config.getRestIdleTimeOut())
                                                            .acceptQueueSize(config.getRestAcceptQueueSize())
                                                            .build();

        httpServer = new HTTPServer(httpServerConfig);
        httpServer.initialize();
    }

    @Override
    public void notifyAfterCompleted() {
        if (config.isEnableDatasourceZipkin()) {
            // Register Zipkin-compatible Tempo API handler with /zipkin context path
            httpServer.addHandler(
                new ZipkinTraceQLApiHandler(getManager()),
                Collections.singletonList(HttpMethod.GET),
                config.getRestContextPathZipkin()
            );
        }

        if (config.isEnableDatasourceSkywalking()) {
            // Register SkyWalking-compatible Tempo API handler with /skywalking context path
            httpServer.addHandler(
                new SkyWalkingTraceQLApiHandler(getManager()),
                Collections.singletonList(HttpMethod.GET),
                config.getRestContextPathSkywalking()
            );
        }
        if (!RunningMode.isInitMode()) {
            httpServer.start();
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME
        };
    }
}
