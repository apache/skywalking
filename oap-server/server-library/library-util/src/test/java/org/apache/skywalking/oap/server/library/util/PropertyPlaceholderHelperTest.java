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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.yaml.snakeyaml.Yaml;

/**
 * @author jian.tan
 */
public class PropertyPlaceholderHelperTest {
    private PropertyPlaceholderHelper placeholderHelper;
    private Properties properties = new Properties();
    private final Yaml yaml = new Yaml();

    /**
     * The EnvironmentVariables rule allows you to set environment variables for your test. All changes to environment
     * variables are reverted after the test.
     */
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables().set("REST_PORT", "12801");

    @SuppressWarnings("unchecked")
    @Before
    public void init() throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    providerConfig.forEach((name, propertiesConfig) -> {
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
        Assert.assertEquals("grpc.skywalking.incubator.apache.org",
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("gRPCHost"), properties)));

        //tests that use ${REST_HOST:0.0.0.0} but not set REST_HOST in environmentVariables.
        Assert.assertEquals("0.0.0.0",
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("restHost"), properties)));

        //tests that use ${REST_PORT:12800} and set REST_PORT in environmentVariables.
        Assert.assertEquals(12801,
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty("restPort"), properties)));
    }

    @After
    public void afterTest() {
        //revert environment variables changes after the test for safe.
        environmentVariables.clear("REST_HOST");
    }
}
