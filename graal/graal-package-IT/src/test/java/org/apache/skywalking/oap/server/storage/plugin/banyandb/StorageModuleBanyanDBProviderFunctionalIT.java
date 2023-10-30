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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.oap.server.core.CoreModule;

import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.testing.module.mock.MockModuleManager;
import org.apache.skywalking.oap.server.testing.module.mock.MockModuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class StorageModuleBanyanDBProviderFunctionalIT {
    private BanyanDBStorageConfig banyanDBStorageConfig;

    @Container
    public final GenericContainer<?> container =
            new GenericContainer<>(DockerImageName.parse("ghcr.io/apache/skywalking-banyandb:dea8c1e37d4dc19fe18397deb576151a22e2fad8"))
                    .waitingFor(Wait.forLogMessage(".*Start liaison http server.*", 1))
                    .withCommand(
                            "standalone",
                            "--stream-root-path", "/tmp/stream-data",
                            "--measure-root-path", "/tmp/measure-data"
                    )
                    .withExposedPorts(17912);
    private ModuleManager moduleManager;

    @BeforeEach
    public void init() {
        moduleManager = new MockModuleManager() {
            @Override
            protected void init() {
                register(CoreModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(ModelCreator.class, new StorageModels());
                    }
                });
                register(TelemetryModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(MetricsCreator.class, new MetricsCreatorNoop());
                    }
                });
            }
        };
    }

    @Test
    public void providerPrepareAndStart() throws ModuleStartException {
        BanyanDBStorageProvider banyanDBStorageProvider = new BanyanDBStorageProvider();
        banyanDBStorageProvider.setManager(moduleManager);

        banyanDBStorageConfig = new BanyanDBStorageConfig();
        banyanDBStorageConfig.setTargets(container.getHost() + ":" + container.getMappedPort(17912));
        banyanDBStorageProvider.newConfigCreator().onInitialized(banyanDBStorageConfig);

        banyanDBStorageProvider.prepare();
        banyanDBStorageProvider.start();
    }
}
