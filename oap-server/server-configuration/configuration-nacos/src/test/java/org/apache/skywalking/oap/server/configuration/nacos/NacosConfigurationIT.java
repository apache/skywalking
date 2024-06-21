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

package org.apache.skywalking.oap.server.configuration.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Testcontainers
public class NacosConfigurationIT {
    @Container
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("nacos/nacos-server:v2.3.2-slim"))
            .waitingFor(Wait.forLogMessage(".*Nacos started successfully.*", 1))
            .withEnv(Collections.singletonMap("MODE", "standalone"))
            .withExposedPorts(8848, 9848);
    private final Yaml yaml = new Yaml();
    private NacosConfigurationTestProvider provider;

    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty("nacos.host", container.getHost());
        System.setProperty("nacos.port", String.valueOf(container.getMappedPort(8848)));

        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager("Test");
        moduleManager.init(applicationConfiguration);

        provider = (NacosConfigurationTestProvider) moduleManager.find(NacosConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
        Integer nacosPortOffset = container.getMappedPort(9848) - container.getMappedPort(8848);
        System.setProperty("nacos.server.grpc.port.offset", nacosPortOffset.toString());
    }

    @AfterEach
    public void after() {
        System.clearProperty("nacos.server.grpc.port.offset");
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    @Timeout(20)
    public void shouldReadUpdated() throws NacosException {
        assertNull(provider.watcher.value());

        final Properties properties = new Properties();
        final String nacosHost = System.getProperty("nacos.host");
        final String nacosPort = System.getProperty("nacos.port");
        log.info("nacosHost: {}, nacosPort: {}", nacosHost, nacosPort);
        properties.put("serverAddr", nacosHost + ":" + nacosPort);

        final ConfigService configService = NacosFactory.createConfigService(properties);
        assertTrue(configService.publishConfig("test-module.default.testKey", "skywalking", "500"));

        for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
        }

        assertEquals("500", provider.watcher.value());

        assertTrue(configService.removeConfig("test-module.default.testKey", "skywalking"));

        for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
        }

        assertNull(provider.watcher.value());
    }

    @Test
    @Timeout(20)
    public void shouldReadUpdatedGroup() throws NacosException {
        assertEquals("{}", provider.groupWatcher.groupItems().toString());

        final Properties properties = new Properties();
        final String nacosHost = System.getProperty("nacos.host");
        final String nacosPort = System.getProperty("nacos.port");
        log.info("nacosHost: {}, nacosPort: {}", nacosHost, nacosPort);
        properties.put("serverAddr", nacosHost + ":" + nacosPort);

        final ConfigService configService = NacosFactory.createConfigService(properties);
        //test add group key and item1 item2
        assertTrue(configService.publishConfig("test-module.default.testKeyGroup", "skywalking", "item1\n item2"));
        assertTrue(configService.publishConfig("item1", "skywalking", "100"));
        assertTrue(configService.publishConfig("item2", "skywalking", "200"));
        for (String v = provider.groupWatcher.groupItems()
                                             .get("item1"); v == null; v = provider.groupWatcher.groupItems()
                                                                                                .get("item1")) {
        }
        for (String v = provider.groupWatcher.groupItems()
                                             .get("item2"); v == null; v = provider.groupWatcher.groupItems()
                                                                                                .get("item2")) {
        }
        assertEquals("100", provider.groupWatcher.groupItems().get("item1"));
        assertEquals("200", provider.groupWatcher.groupItems().get("item2"));

        //test remove item1
        assertTrue(configService.removeConfig("item1", "skywalking"));
        for (String v = provider.groupWatcher.groupItems()
                                             .get("item1"); v != null; v = provider.groupWatcher.groupItems()
                                                                                                .get("item1")) {
        }
        assertNull(provider.groupWatcher.groupItems().get("item1"));

        //test modify item1
        assertTrue(configService.publishConfig("item1", "skywalking", "300"));
        for (String v = provider.groupWatcher.groupItems()
                                             .get("item1"); v == null; v = provider.groupWatcher.groupItems()
                                                                                                .get("item1")) {
        }
        assertEquals("300", provider.groupWatcher.groupItems().get("item1"));

        //test remove group key
        assertTrue(configService.removeConfig("test-module.default.testKeyGroup", "skywalking"));
        for (String v = provider.groupWatcher.groupItems()
                                             .get("item2"); v != null; v = provider.groupWatcher.groupItems()
                                                                                                .get("item2")) {
        }
        assertNull(provider.groupWatcher.groupItems().get("item2"));
        //chean
        assertTrue(configService.removeConfig("item1", "skywalking"));
        assertTrue(configService.removeConfig("item2", "skywalking"));
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(
                        moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> {
                                properties.put(key, value);
                                final Object replaceValue = yaml.load(
                                    PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value + "", properties));
                                if (replaceValue != null) {
                                    properties.replace(key, replaceValue);
                                }
                            });
                        }
                        moduleConfiguration.addProviderConfiguration(name, properties);
                    });
                }
            });
        }
    }
}
