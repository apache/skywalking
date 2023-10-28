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

import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.log.analyzer.provider.log.LogAnalyzerServiceImpl;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleProvider;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserServiceImpl;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.SourceReceiverImpl;

import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class KafkaFetcherModuleStartIT {

    @Mock
    private NoneTelemetryProvider telemetryProvider;
    @Mock
    private CoreModuleProvider coreModuleProvider;

    @Mock
    private LogAnalyzerModuleProvider logAnalyzerModuleProvider;
    @Mock
    private AnalyzerModuleProvider analyzerModuleProvider;
    @Mock
    private ModuleManager moduleManager;
    @Container
    KafkaContainer container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.1.0-1-ubi8"));

    @BeforeEach
    public void init() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
                .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        CoreModule coreModule = Mockito.spy(CoreModule.class);
        Mockito.when(coreModuleProvider.getService(SourceReceiver.class)).thenReturn(new SourceReceiverImpl());
        Whitebox.setInternalState(coreModule, "loadedProvider", coreModuleProvider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);

        AnalyzerModule analyzerModule = Mockito.spy(AnalyzerModule.class);
        Mockito.when(moduleManager.find(AnalyzerModule.NAME)).thenReturn(analyzerModule);
        Whitebox.setInternalState(analyzerModule, "loadedProvider", analyzerModuleProvider);
        Mockito.when(analyzerModuleProvider.getService(ISegmentParserService.class)).thenReturn(new SegmentParserServiceImpl(moduleManager, new AnalyzerModuleConfig()));

        LogAnalyzerModule logAnalyzerModule = Mockito.spy(LogAnalyzerModule.class);
        Mockito.when(moduleManager.find(LogAnalyzerModule.NAME)).thenReturn(logAnalyzerModule);
        Whitebox.setInternalState(logAnalyzerModule, "loadedProvider", logAnalyzerModuleProvider);
        Mockito.when(logAnalyzerModuleProvider.getService(ILogAnalyzerService.class)).thenReturn(new LogAnalyzerServiceImpl(moduleManager, new LogAnalyzerModuleConfig()));
    }

    @Test
    public void startProvider() throws ModuleStartException {
        KafkaFetcherProvider kafkaFetcherProvider = new KafkaFetcherProvider();
        kafkaFetcherProvider.setManager(moduleManager);
        KafkaFetcherConfig kafkaFetcherConfig = new KafkaFetcherConfig();
        kafkaFetcherConfig.setReplicationFactor(1);
        kafkaFetcherConfig.setBootstrapServers(container.getBootstrapServers());

        kafkaFetcherProvider.newConfigCreator().onInitialized(kafkaFetcherConfig);

        kafkaFetcherProvider.prepare();
        kafkaFetcherProvider.start();
    }
}
