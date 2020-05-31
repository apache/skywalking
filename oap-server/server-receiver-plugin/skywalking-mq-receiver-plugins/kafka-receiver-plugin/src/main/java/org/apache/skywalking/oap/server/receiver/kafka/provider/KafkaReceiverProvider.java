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

package org.apache.skywalking.oap.server.receiver.kafka.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.kafka.KafkaReceiveHandlerRegister;
import org.apache.skywalking.oap.server.receiver.kafka.module.KafkaReceiverConfig;
import org.apache.skywalking.oap.server.receiver.kafka.provider.handler.JVMMetricsReceiveHandler;
import org.apache.skywalking.oap.server.receiver.kafka.provider.handler.KafkaManagementReceiveHandler;
import org.apache.skywalking.oap.server.receiver.kafka.provider.handler.TraceSegmentReceiveHandler;
import org.apache.skywalking.oap.server.receiver.mq.ReceiveHandlerRegister;
import org.apache.skywalking.oap.server.receiver.mq.module.MessageQueueReceiverModule;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.MultiScopesAnalysisListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

@Slf4j
public class KafkaReceiverProvider extends ModuleProvider {
    private KafkaReceiverConfig config;
    private KafkaReceiveHandlerRegister handlerRegister;
    private TraceServiceModuleConfig traceServiceModuleConfig;

    public KafkaReceiverProvider() {
        config = new KafkaReceiverConfig();
    }

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return MessageQueueReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        handlerRegister = new KafkaReceiveHandlerRegister(config);
        this.registerServiceImplementation(ReceiveHandlerRegister.class, handlerRegister);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        handlerRegister.register(new JVMMetricsReceiveHandler(getManager(), config));
        handlerRegister.register(new KafkaManagementReceiveHandler(getManager(), config));
        handlerRegister.register(new TraceSegmentReceiveHandler(
                                     getManager(),
                                     listenerManager(),
                                     config
                                 )
        );
        handlerRegister.prepare();
        handlerRegister.start();
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            ClusterModule.NAME,
            TelemetryModule.NAME,
            CoreModule.NAME,
            SharingServerModule.NAME,
            ConfigurationModule.NAME
        };
    }

    private SegmentParserListenerManager listenerManager() {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (traceServiceModuleConfig.isTraceAnalysis()) {
            listenerManager.add(new MultiScopesAnalysisListener.Factory(getManager()));
            listenerManager.add(new NetworkAddressAliasMappingListener.Factory(getManager()));
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(getManager(), traceServiceModuleConfig));

        return listenerManager;
    }
}
