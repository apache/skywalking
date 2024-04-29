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

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.ZipkinSpanHTTPHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.kafka.KafkaHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class ZipkinReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private ZipkinReceiverConfig config;
    private HTTPServer httpServer;
    private KafkaHandler kafkaHandler;
    private SpanForward spanForward;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ZipkinReceiverModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<ZipkinReceiverConfig>() {
            @Override
            public Class type() {
                return ZipkinReceiverConfig.class;
            }

            @Override
            public void onInitialized(final ZipkinReceiverConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        this.spanForward = new SpanForward(config, getManager());
        this.registerServiceImplementation(SpanForwardService.class, spanForward);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (config.getSampleRate() < 0 || config.getSampleRate() > 10000) {
            throw new IllegalArgumentException(
                "sampleRate: " + config.getSampleRate() + ", should be between 0 and 10000");
        }

        if (config.isEnableHttpCollector()) {
            HTTPServerConfig httpServerConfig = HTTPServerConfig.builder()
                                                                .host(config.getRestHost())
                                                                .port(config.getRestPort())
                                                                .contextPath(config.getRestContextPath())
                                                                .idleTimeOut(config.getRestIdleTimeOut())
                                                                .maxThreads(config.getRestMaxThreads())
                                                                .acceptQueueSize(config.getRestAcceptQueueSize())
                                                                .build();

            httpServer = new HTTPServer(httpServerConfig);
            httpServer.initialize();

            httpServer.addHandler(
                new ZipkinSpanHTTPHandler(this.spanForward, getManager()),
                Arrays.asList(HttpMethod.POST, HttpMethod.GET)
            );
        }

        if (config.isEnableKafkaCollector()) {
            kafkaHandler = new KafkaHandler(config, this.spanForward, getManager());
        }
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        if (config.isEnableHttpCollector() && !RunningMode.isInitMode()) {
            httpServer.start();
        }

        if (config.isEnableKafkaCollector() && !RunningMode.isInitMode()) {
            kafkaHandler.start();
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME
        };
    }
}
