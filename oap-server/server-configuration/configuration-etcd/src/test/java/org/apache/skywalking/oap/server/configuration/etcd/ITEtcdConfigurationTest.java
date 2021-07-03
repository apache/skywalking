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
import java.io.FileNotFoundException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleConfigException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleNotFoundException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Slf4j
public class ITEtcdConfigurationTest {
    @ClassRule
    public static final GenericContainer CONTAINER =
        new GenericContainer(DockerImageName.parse("bitnami/etcd:3.5.0"))
            .waitingFor(Wait.forLogMessage(".*etcd setup finished!.*", 1))
            .withEnv(Collections.singletonMap("ALLOW_NONE_AUTHENTICATION", "yes"));

    private static EtcdConfigurationTestProvider PROVIDER;

    private static final String TEST_VALUE = "value";

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException, ModuleConfigException, ModuleNotFoundException, ModuleStartException {
        System.setProperty("etcd.endpoint", "http://127.0.0.1:" + CONTAINER.getMappedPort(2379));

        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        PROVIDER = (EtcdConfigurationTestProvider) moduleManager.find(EtcdConfigurationTestModule.NAME).provider();

        assertNotNull(PROVIDER);
    }

    @Test(timeout = 20000)
    public void shouldReadUpdated() throws Exception {
        assertNull(PROVIDER.watcher.value());

        KV client = Client.builder()
                          .endpoints("http://localhost:" + CONTAINER.getMappedPort(2379))
                          .namespace(ByteSequence.from("/skywalking/", Charset.defaultCharset()))
                          .build()
                          .getKVClient();

        client.put(
            ByteSequence.from("test-module.default.testKey", Charset.defaultCharset()),
            ByteSequence.from(TEST_VALUE, Charset.defaultCharset())
        ).get();

        for (String v = PROVIDER.watcher.value(); v == null; v = PROVIDER.watcher.value()) {
            log.info("value is : {}", PROVIDER.watcher.value());
            TimeUnit.MILLISECONDS.sleep(200L);
        }

        assertEquals(TEST_VALUE, PROVIDER.watcher.value());

        client.delete(ByteSequence.from("test-module.default.testKey", Charset.defaultCharset())).get();

        for (String v = PROVIDER.watcher.value(); v != null; v = PROVIDER.watcher.value()) {
            TimeUnit.MILLISECONDS.sleep(200L);
        }

        assertNull(PROVIDER.watcher.value());
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

    @AfterClass
    public static void teardown() {
        CONTAINER.close();
    }
}
