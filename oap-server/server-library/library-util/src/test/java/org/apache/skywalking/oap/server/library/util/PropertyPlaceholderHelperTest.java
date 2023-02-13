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

package org.apache.skywalking.oap.server.library.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaml.snakeyaml.Yaml;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SystemStubsExtension.class)
public class PropertyPlaceholderHelperTest {
    private PropertyPlaceholderHelper placeholderHelper;
    private final Properties properties = new Properties();
    private final Yaml yaml = new Yaml();

    @SystemStub
    private final EnvironmentVariables justForSideEffect = new EnvironmentVariables().set("REST_PORT", "12801");

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void init() throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Object>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                selectConfig(providerConfig);
                if (providerConfig.size() > 0) {
                    providerConfig.forEach((name, config) -> {
                        final Map<String, ?> propertiesConfig = (Map<String, ?>) config;
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> properties.put(key, value));
                        }
                    });
                }
            });
        }
        placeholderHelper = PropertyPlaceholderHelper.INSTANCE;
    }

    @Test
    public void testDataType() {
        //tests that do not use ${name} to set config.
        assertEquals("grpc.skywalking.apache.org", yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("gRPCHost"), properties)));

        //tests that use ${REST_HOST:0.0.0.0} but not set REST_HOST in environmentVariables.
        assertEquals("0.0.0.0", yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("restHost"), properties)));

        //tests that use ${REST_PORT:12800} and set REST_PORT in environmentVariables.
        assertEquals((Integer) 12801, yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("restPort"), properties)));
    }

    @Test
    public void testReplacePlaceholders() {
        PropertyPlaceholderHelper propertyPlaceholderHelper = PropertyPlaceholderHelper.INSTANCE;
        Properties properties = new Properties();
        String resultString = propertyPlaceholderHelper.replacePlaceholders("&${[}7", properties);

        assertEquals(0, properties.size());
        Assertions.assertTrue(properties.isEmpty());

        assertNotNull(resultString);
        assertEquals("&${[}7", resultString);
    }

    private void selectConfig(final Map<String, Object> configuration) {
        if (configuration.size() <= 1) {
            return;
        }
        if (configuration.containsKey("selector")) {
            final String selector = (String) configuration.get("selector");
            final String resolvedSelector = PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(
                selector, System.getProperties()
            );
            configuration.entrySet().removeIf(e -> !resolvedSelector.equals(e.getKey()));
        }
    }

}
