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

package org.apache.skywalking.oap.server.receiver.aws.firehose;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverModule;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;

@Slf4j
public class AWSFirehoseReceiverModuleProvider extends ModuleProvider {
    public static final String NAME = "default";

    private AWSFirehoseReceiverModuleConfig moduleConfig;
    private HTTPServer httpServer;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AWSFirehoseReceiverModule.class;
    }

    @Override
    public ConfigCreator<AWSFirehoseReceiverModuleConfig> newConfigCreator() {
        return new ConfigCreator<AWSFirehoseReceiverModuleConfig>() {
            @Override
            public Class<AWSFirehoseReceiverModuleConfig> type() {
                return AWSFirehoseReceiverModuleConfig.class;
            }

            @Override
            public void onInitialized(final AWSFirehoseReceiverModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        final HTTPServerConfig httpServerConfig = HTTPServerConfig.builder().host(moduleConfig.getHost())
                                                                  .port(moduleConfig.getPort())
                                                                  .contextPath(moduleConfig.getContextPath())
                                                                  .maxThreads(moduleConfig.getMaxThreads())
                                                                  .idleTimeOut(moduleConfig.getIdleTimeOut())
                                                                  .acceptQueueSize(
                                                                      moduleConfig.getAcceptQueueSize())
                                                                  .maxRequestHeaderSize(
                                                                      moduleConfig.getMaxRequestHeaderSize())
                                                                  //set acceptProxyRequest same with enableTLS
                                                                  .acceptProxyRequest(
                                                                      moduleConfig.isEnableTLS())
                                                                  .enableTLS(moduleConfig.isEnableTLS())
                                                                  .tlsKeyPath(moduleConfig.getTlsKeyPath())
                                                                  .tlsCertChainPath(moduleConfig.getTlsCertChainPath())
                                                                  .build();
        httpServer = new HTTPServer(httpServerConfig);
        httpServer.initialize();
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        final OpenTelemetryMetricRequestProcessor processor = getManager().find(OtelMetricReceiverModule.NAME)
                                                                          .provider()
                                                                          .getService(
                                                                              OpenTelemetryMetricRequestProcessor.class);
        httpServer.addHandler(
            new FirehoseHTTPHandler(processor, moduleConfig.getFirehoseAccessKey()),
            Collections.singletonList(HttpMethod.POST)
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (!RunningMode.isInitMode()) {
            httpServer.start();
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            OtelMetricReceiverModule.NAME
        };
    }
}
