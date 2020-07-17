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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler.JVMMetricsHandler;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler.ManagementHandler;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler.ProfileTaskHandler;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler.TraceSegmentHandler;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodeUpdate;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.KafkaFetcherHandlerRegister;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

@Slf4j
public class KafkaFetcherProvider extends ModuleProvider {
    private KafkaFetcherConfig config;
    private KafkaFetcherHandlerRegister handlerRegister;

    public KafkaFetcherProvider() {
        config = new KafkaFetcherConfig();
    }

    @Override
    public String name() {
        return "kafka-consumer";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return KafkaFetcherModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        handlerRegister = new KafkaFetcherHandlerRegister(config);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

        if (handlerRegister.isSharding() && getManager().has(ClusterModule.NAME)) {
            ClusterNodeUpdate updater = getManager().find(ClusterModule.NAME)
                                                    .provider()
                                                    .getService(ClusterNodeUpdate.class);
            try {
                updater.updateRemoteNodes(config.getServerId().toString());
            } catch (Exception e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }

        handlerRegister.register(new JVMMetricsHandler(getManager(), config));
        handlerRegister.register(new ManagementHandler(getManager(), config));
        handlerRegister.register(new TraceSegmentHandler(getManager(), config));
        handlerRegister.register(new ProfileTaskHandler(getManager(), config));
        handlerRegister.start();
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            "receiver-trace",
            ClusterModule.NAME,
            TelemetryModule.NAME,
            CoreModule.NAME,
            ConfigurationModule.NAME
        };
    }

}
