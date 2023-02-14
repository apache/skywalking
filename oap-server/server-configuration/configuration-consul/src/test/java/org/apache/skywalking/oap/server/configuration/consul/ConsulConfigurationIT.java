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

package org.apache.skywalking.oap.server.configuration.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class ConsulConfigurationIT {
    private final Yaml yaml = new Yaml();

    private ConsulConfigurationTestProvider provider;

    @Container
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("consul:0.9"))
            .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1))
            .withCommand("agent", "-server", "-bootstrap-expect=1", "-client=0.0.0.0")
            .withExposedPorts(8500);

    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty("consul.address", container.getHost() + ":" + container.getMappedPort(8500));
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider = (ConsulConfigurationTestProvider) moduleManager.find(ConsulConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @Test
    @Timeout(60)
    public void shouldReadUpdated() {
        assertNull(provider.watcher.value());

        String hostAndPort = System.getProperty("consul.address", "127.0.0.1:8500");
        Consul consul = Consul.builder()
                              .withHostAndPort(HostAndPort.fromString(hostAndPort))
                              .withConnectTimeoutMillis(5000)
                              .build();
        KeyValueClient client = consul.keyValueClient();

        assertTrue(client.putValue("test-module.default.testKey", "1000"));

        for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
        }

        assertEquals("1000", provider.watcher.value());

        client.deleteKey("test-module.default.testKey");

        for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
        }

        assertNull(provider.watcher.value());
    }

    @Test
    @Timeout(30)
    public void shouldReadUpdated4Group() {
        assertEquals("{}", provider.groupWatcher.groupItems().toString());

        String hostAndPort = System.getProperty("consul.address", "127.0.0.1:8500");
        Consul consul = Consul.builder()
                              .withHostAndPort(HostAndPort.fromString(hostAndPort))
                              .withConnectTimeoutMillis(5000)
                              .build();
        KeyValueClient client = consul.keyValueClient();

        assertTrue(client.putValue("test-module.default.testKeyGroup/item1", "100"));
        assertTrue(client.putValue("test-module.default.testKeyGroup/item2", "200"));

        for (String v = provider.groupWatcher.groupItems().get("item1"); v == null; v = provider.groupWatcher.groupItems().get("item1")) {
        }
        for (String v = provider.groupWatcher.groupItems().get("item2"); v == null; v = provider.groupWatcher.groupItems().get("item2")) {
        }
        assertEquals("100", provider.groupWatcher.groupItems().get("item1"));
        assertEquals("200", provider.groupWatcher.groupItems().get("item2"));

        //test remove item1
        client.deleteKey("test-module.default.testKeyGroup/item1");
        for (String v = provider.groupWatcher.groupItems().get("item1"); v != null; v = provider.groupWatcher.groupItems().get("item1")) {
        }
        assertNull(provider.groupWatcher.groupItems().get("item1"));

        //test modify item2
        client.putValue("test-module.default.testKeyGroup/item2", "300");
        for (String v = provider.groupWatcher.groupItems().get("item2"); v.equals("200"); v = provider.groupWatcher.groupItems().get("item2")) {
        }
        assertEquals("300", provider.groupWatcher.groupItems().get("item2"));

        //chean
        client.deleteKey("test-module.default.testKeyGroup/item2");
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> {
                                properties.put(key, value);
                                final Object replaceValue = yaml.load(PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value + "", properties));
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
