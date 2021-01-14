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

package org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.PulsarFetcherHandlerRegister;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.module.PulsarFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.module.PulsarFetcherModule;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.JVMMetricsHandler;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.MeterServiceHandler;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.ProfileTaskHandler;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.ServiceManagementHandler;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.TraceSegmentHandler;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

@Slf4j
public class PulsarFetcherProvider extends ModuleProvider {

    private PulsarFetcherHandlerRegister handlerRegister;
    private PulsarFetcherConfig config;

    private IMeterProcessService processService;

    public PulsarFetcherProvider() {
        config = new PulsarFetcherConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return PulsarFetcherModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            handlerRegister = new PulsarFetcherHandlerRegister(config);
        } catch (PulsarClientException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() throws ServiceNotProvidedException {
        handlerRegister.register(new JVMMetricsHandler(getManager(), config));
        handlerRegister.register(new ServiceManagementHandler(getManager(), config));
        handlerRegister.register(new TraceSegmentHandler(getManager(), config));
        handlerRegister.register(new ProfileTaskHandler(getManager(), config));

        if (config.isEnableMeterSystem()) {
            processService = getManager().find(AnalyzerModule.NAME).provider().getService(IMeterProcessService.class);
            handlerRegister.register(new MeterServiceHandler(getManager(), config));
        }
        try {
            handlerRegister.start();
        } catch (PulsarClientException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
    }

    @Override
    public String[] requiredModules() {
        return new String[]{
                AnalyzerModule.NAME,
                CoreModule.NAME
        };
    }

}
