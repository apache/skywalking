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

package org.apache.skywalking.oap.query.logql;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import org.apache.skywalking.oap.query.logql.handler.LogQLApiHandler;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;

public class LogQLProvider extends ModuleProvider {
    public static final String NAME = "default";
    private LogQLConfig config;
    private HTTPServer httpServer;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return LogQLModule.class;
    }

    @Override
    public ConfigCreator<LogQLConfig> newConfigCreator() {
        return new ConfigCreator<>() {
            @Override
            public Class<LogQLConfig> type() {
                return LogQLConfig.class;
            }

            @Override
            public void onInitialized(final LogQLConfig initialized) {
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
                                                            .contextPath(config.getRestContextPath())
                                                            .idleTimeOut(config.getRestIdleTimeOut())
                                                            .acceptQueueSize(config.getRestAcceptQueueSize())
                                                            .build();

        httpServer = new HTTPServer(httpServerConfig);
        httpServer.initialize();
        httpServer.addHandler(
            new LogQLApiHandler(getManager()),
            Arrays.asList(HttpMethod.POST, HttpMethod.GET)
        );
    }

    @Override
    public void notifyAfterCompleted() {
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
