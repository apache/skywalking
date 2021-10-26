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
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ITConsulConfigurationTest {
    private final Yaml yaml = new Yaml();

    private ConsulConfigurationTestProvider provider;

    @Rule
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("consul:0.9"))
            .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1))
            .withCommand("agent", "-server", "-bootstrap-expect=1", "-client=0.0.0.0");

    @Before
    public void setUp() throws Exception {
        System.setProperty("consul.address", container.getHost() + ":" + container.getMappedPort(8500));
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider = (ConsulConfigurationTestProvider) moduleManager.find(ConsulConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @Test(timeout = 60000)
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

    @Test(timeout = 30000)
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
