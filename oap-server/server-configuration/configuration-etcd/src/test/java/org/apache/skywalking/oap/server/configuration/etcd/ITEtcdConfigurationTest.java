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

package org.apache.skywalking.oap.server.configuration.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
@Testcontainers
public class ITEtcdConfigurationTest {
    @Container
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.5.0"))
            .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1))
            .withEnv(Collections.singletonMap("ALLOW_NONE_AUTHENTICATION", "yes"))
            .withCommand(
                "etcd",
                "--advertise-client-urls", "http://0.0.0.0:2379",
                "--listen-client-urls", "http://0.0.0.0:2379"
            )
            .withExposedPorts(2379);

    private EtcdConfigurationTestProvider provider;

    @BeforeEach
    public void before() throws Exception {
        System.setProperty("etcd.endpoint", "http://127.0.0.1:" + container.getMappedPort(2379));

        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider = (EtcdConfigurationTestProvider) moduleManager.find(EtcdConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @Test
    @Timeout(20)
    public void shouldReadUpdated() throws Exception {
        assertNull(provider.watcher.value());

        KV client = Client.builder()
                          .endpoints("http://localhost:" + container.getMappedPort(2379))
                          .namespace(ByteSequence.from("/skywalking/", Charset.defaultCharset()))
                          .build()
                          .getKVClient();

        String testValue = "value";
        client.put(
            ByteSequence.from("test-module.default.testKey", Charset.defaultCharset()),
            ByteSequence.from(testValue, Charset.defaultCharset())
        ).get();

        for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
            log.info("value is : {}", provider.watcher.value());
            TimeUnit.MILLISECONDS.sleep(200L);
        }

        assertEquals(testValue, provider.watcher.value());

        client.delete(ByteSequence.from("test-module.default.testKey", Charset.defaultCharset())).get();

        for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
            TimeUnit.MILLISECONDS.sleep(200L);
        }

        assertNull(provider.watcher.value());
    }

    @Test
    @Timeout(20)
    public void shouldReadUpdated4Group() throws Exception {
        assertEquals("{}", provider.groupWatcher.groupItems().toString());

        KV client = Client.builder()
                          .endpoints("http://localhost:" + container.getMappedPort(2379))
                          .namespace(ByteSequence.from("/skywalking/", Charset.defaultCharset()))
                          .build()
                          .getKVClient();

        client.put(
            ByteSequence.from("test-module.default.testKeyGroup/item1", Charset.defaultCharset()),
            ByteSequence.from("100", Charset.defaultCharset())
        ).get();
        client.put(
            ByteSequence.from("test-module.default.testKeyGroup/item2", Charset.defaultCharset()),
            ByteSequence.from("200", Charset.defaultCharset())
        ).get();

        for (String v = provider.groupWatcher.groupItems().get("item1"); v == null; v = provider.groupWatcher.groupItems().get("item1")) {
            log.info("value is : {}", provider.groupWatcher.groupItems().get("item1"));
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        for (String v = provider.groupWatcher.groupItems().get("item2"); v == null; v = provider.groupWatcher.groupItems().get("item2")) {
            log.info("value is : {}", provider.groupWatcher.groupItems().get("item2"));
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        assertEquals("100", provider.groupWatcher.groupItems().get("item1"));
        assertEquals("200", provider.groupWatcher.groupItems().get("item2"));

        //test remove item1
        client.delete(ByteSequence.from("test-module.default.testKeyGroup/item1", Charset.defaultCharset())).get();
        for (String v = provider.groupWatcher.groupItems().get("item1"); v != null; v = provider.groupWatcher.groupItems().get("item1")) {
            log.info("value is : {}", provider.groupWatcher.groupItems().get("item1"));
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        assertNull(provider.groupWatcher.groupItems().get("item1"));

        //test modify item2
        client.put(
            ByteSequence.from("test-module.default.testKeyGroup/item2", Charset.defaultCharset()),
            ByteSequence.from("300", Charset.defaultCharset())
        ).get();
        for (String v = provider.groupWatcher.groupItems().get("item2"); v.equals("200"); v = provider.groupWatcher.groupItems().get("item2")) {
            log.info("value is : {}", provider.groupWatcher.groupItems().get("item2"));
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        assertEquals("300", provider.groupWatcher.groupItems().get("item2"));
    }

    @SuppressWarnings("unchecked")
    private static void loadConfig(ApplicationConfiguration configuration) throws FileNotFoundException {
        final Yaml yaml = new Yaml();

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
