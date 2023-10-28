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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchProvider;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class StorageModuleElasticSearchProviderFunctionalIT {
    private StorageModuleElasticsearchConfig elasticsearchConfig;

    @Container
    public final GenericContainer<?> container =
            new GenericContainer<>(DockerImageName.parse("elasticsearch:7.17.12"))
                    .waitingFor(Wait.forHttp("/_cluster/health"))
                    .withEnv("discovery.type", "single-node")
                    .withExposedPorts(9200);
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;
    @Mock
    private CoreModuleProvider coreModuleProvider;

    @BeforeEach
    public void init() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
                .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        CoreModule coreModule = Mockito.spy(CoreModule.class);
        Mockito.when(coreModuleProvider.getService(ModelCreator.class)).thenReturn(new StorageModels());
        Mockito.when(coreModuleProvider.getService(ConfigService.class)).thenReturn(new ConfigService(new CoreModuleConfig(), coreModuleProvider));
        Whitebox.setInternalState(coreModule, "loadedProvider", coreModuleProvider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
    }

    @Test
    public void providerPrepareAndStart() throws ModuleStartException {
        StorageModuleElasticsearchProvider elasticsearchProvider = new StorageModuleElasticsearchProvider();
        elasticsearchProvider.setManager(moduleManager);
        elasticsearchConfig = new StorageModuleElasticsearchConfig();
        elasticsearchConfig.setClusterNodes(container.getHost() + ":" + container.getMappedPort(9200));
        elasticsearchProvider.newConfigCreator().onInitialized(elasticsearchConfig);

        elasticsearchProvider.prepare();
        elasticsearchProvider.start();
    }
}
