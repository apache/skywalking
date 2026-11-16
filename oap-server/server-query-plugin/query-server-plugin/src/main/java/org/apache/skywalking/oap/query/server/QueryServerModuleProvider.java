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

package org.apache.skywalking.oap.query.server;

import java.util.Objects;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class QueryServerModuleProvider extends ModuleProvider {

    private QueryServerConfig config;
    private HTTPServer httpServer;
    /**
     * Where the query handlers go when the query server has no port of its own. The module manager refuses
     * lookups during prepare, so this is resolved in {@link #start()}; every module that adds a query handler
     * requires query-server, so its start runs after this one.
     */
    private HTTPHandlerRegister coreRESTRegister;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return QueryServerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<QueryServerConfig>() {
            @Override
            public Class type() {
                return QueryServerConfig.class;
            }

            @Override
            public void onInitialized(final QueryServerConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() {
        if (config.getRestPort() > 0) {
            final String host = StringUtil.isBlank(config.getRestHost()) ? "0.0.0.0" : config.getRestHost();
            final HTTPServerConfig httpServerConfig =
                HTTPServerConfig.builder()
                                .host(host)
                                .port(config.getRestPort())
                                .contextPath(config.getRestContextPath())
                                .idleTimeOut(config.getRestIdleTimeOut())
                                .acceptQueueSize(config.getRestAcceptQueueSize())
                                .maxRequestHeaderSize(config.getHttpMaxRequestHeaderSize())
                                .enableTLS(config.isRestSSLEnabled())
                                .tlsKeyPath(config.getRestSSLKeyPath())
                                .tlsCertChainPath(config.getRestSSLCertChainPath())
                                .build();
            setBootingParameter("oap.query.http.host", host);
            setBootingParameter("oap.query.http.port", config.getRestPort());

            httpServer = new HTTPServer(httpServerConfig);
            httpServer.setBlockingTaskName("query-http");
            httpServer.initialize();
            registerServiceImplementation(HTTPHandlerRegister.class, new HTTPHandlerRegisterImpl(httpServer));
        } else {
            final HTTPHandlerRegister toCoreREST =
                (httpService, httpMethods) -> coreRESTRegister.addHandler(httpService, httpMethods);
            registerServiceImplementation(HTTPHandlerRegister.class, toCoreREST);
        }
    }

    @Override
    public void start() {
        if (Objects.isNull(httpServer)) {
            coreRESTRegister = getManager().find(CoreModule.NAME)
                                           .provider()
                                           .getService(HTTPHandlerRegister.class);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public void notifyBootCompleted() {
        if (Objects.nonNull(httpServer) && !RunningMode.isInitMode()) {
            httpServer.start();
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
