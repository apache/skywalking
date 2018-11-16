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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * @author jian.tan
 */
public class PropertyPlaceholderHelperTest {
    private PropertyPlaceholderHelper placeholderHelper;
    private Properties properties = new Properties();
    private final Yaml yaml = new Yaml();

    @SuppressWarnings("unchecked")
    @Before
    public void init() {
        try {
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        placeholderHelper =
            new PropertyPlaceholderHelper(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX,
                PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX,
                PlaceholderConfigurerSupport.DEFAULT_VALUE_SEPARATOR, true);
    }

    @Test
    public void testDataType() {
        String hostProp = "restHost";
        String portProp = "restPort";
        String esAddress = "clusterNodes";
        String bufferFileCleanWhenRestartProp = "bufferFileCleanWhenRestart";
        Assert.assertEquals("0.0.0.0",
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty(hostProp), properties)));
        Assert.assertEquals("localhost:9200",
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty(esAddress), properties)));
        Assert.assertEquals(12800,
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty(portProp), properties)));
        Assert.assertEquals(false,
            yaml.load(placeholderHelper.replacePlaceholders(properties.getProperty(bufferFileCleanWhenRestartProp), properties)));
    }
}
