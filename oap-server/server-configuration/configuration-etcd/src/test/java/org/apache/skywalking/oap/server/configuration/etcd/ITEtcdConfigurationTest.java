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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ITEtcdConfigurationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITEtcdConfigurationTest.class);

    private final Yaml yaml = new Yaml();

    private EtcdServerSettings settings;

    private EtcdConfigurationTestProvider provider;

    private EtcdClient client;

    @Before
    public void setUp() throws Exception {
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        final String etcdHost = System.getProperty("etcd.host");
        final String etcdPort = System.getProperty("etcd.port");
        LOGGER.info("etcdHost: {}, etcdPort: {}", etcdHost, etcdPort);
        Properties properties = new Properties();
        properties.setProperty("serverAddr", etcdHost + ":" + etcdPort);

        List<URI> uris = EtcdUtils.parseProp(properties);
        client = new EtcdClient(uris.toArray(new URI[] {}));

        provider = (EtcdConfigurationTestProvider) moduleManager.find(EtcdConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @Test(timeout = 20000)
    public void shouldReadUpdated() throws Exception {
        assertNull(provider.watcher.value());

        assertTrue(publishConfig("test-module.default.testKey", "skywalking", "500"));

        for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
            LOGGER.info("value is : {}", provider.watcher.value());
        }

        assertEquals("500", provider.watcher.value());

        assertTrue(removeConfig("test-module.default.testKey", "skywalking"));

        for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
        }

        assertNull(provider.watcher.value());
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

    private boolean publishConfig(String key, String group, String value) {
        try {
            client.putDir(group).send().get();
            EtcdResponsePromise<EtcdKeysResponse> promise = client.put(generateKey(key, group), value).send();
            promise.get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean removeConfig(String key, String group) throws Exception {
        client.delete(generateKey(key, group)).send().get();
        return true;
    }

    private String generateKey(String key, String group) {
        return new StringBuilder("/").append(group).append("/").append(key).toString();
    }

}
