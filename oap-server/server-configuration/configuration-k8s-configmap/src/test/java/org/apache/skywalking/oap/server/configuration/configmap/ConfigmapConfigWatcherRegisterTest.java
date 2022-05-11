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

package org.apache.skywalking.oap.server.configuration.configmap;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.yaml.snakeyaml.Yaml;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
@PrepareForTest({ConfigurationConfigmapInformer.class})
public class ConfigmapConfigWatcherRegisterTest {

    private ConfigmapConfigurationWatcherRegister register;

    private ConfigurationConfigmapInformer informer;

    private final Yaml yaml = new Yaml();

    @Before
    public void prepare() throws IllegalAccessException {
        ConfigmapConfigurationSettings settings = new ConfigmapConfigurationSettings();
        settings.setPeriod(60);
        informer = PowerMockito.mock(ConfigurationConfigmapInformer.class);
        register = new ConfigmapConfigurationWatcherRegister(settings, informer);
    }

    @Test
    public void readConfigWhenConfigMapDataIsNull() throws Exception {
        Map<String, String> configMapData = new HashMap<>();
        PowerMockito.doReturn(configMapData).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assert.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();
        Assert.assertEquals(configTable.getItems().size(), 1);
        Assert.assertEquals(configTable.getItems().get(0).getName(), "key1");
        Assert.assertNull(configTable.getItems().get(0).getValue());
    }

    @Test
    public void readConfigWhenInformerNotwork() throws Exception {
        PowerMockito.doReturn(new HashMap<>()).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assert.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();
        Assert.assertEquals(configTable.getItems().size(), 1);
        Assert.assertEquals(configTable.getItems().get(0).getName(), "key1");
        Assert.assertNull(configTable.getItems().get(0).getValue());
    }

    @Test
    public void readConfigWhenInformerWork() throws Exception {
        Map<String, String> configMapData = this.readMockConfigMapData();
        PowerMockito.doReturn(configMapData).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("agent-analyzer.default.slowDBAccessThreshold");
            add("alarm.default.alarm-settings");
            add("core.default.apdexThreshold");
            add("agent-analyzer.default.uninstrumentedGateways");
        }});

        Assert.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();

        List<String> list = configTable.getItems().stream()
                                       .map(ConfigTable.ConfigItem::getValue)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toList());
        Assert.assertEquals(list.size(), 4);
    }

    @Test
    public void readGroupConfigWhenConfigMapDataIsNull() throws Exception {
        Map<String, String> configMapData = new HashMap<>();
        PowerMockito.doReturn(configMapData).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assert.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();
        Assert.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "key1");
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 0);
    }

    @Test
    public void readGroupConfigWhenInformerNotwork() throws Exception {
        PowerMockito.doReturn(new HashMap<>()).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assert.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();
        Assert.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "key1");
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 0);
    }

    @Test
    public void readGroupConfigWhenInformerWork() throws Exception {
        Map<String, String> configMapData = this.readMockConfigMapData();
        PowerMockito.doReturn(configMapData).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("core.default.endpoint-name-grouping-openapi");
        }});

        Assert.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();

        Assert.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "core.default.endpoint-name-grouping-openapi");
        Assert.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 3);
    }

    private Map<String, String> readMockConfigMapData() throws FileNotFoundException {
        Reader configmapReader1 = ResourceUtils.read("skywalking-dynamic-configmap.example.yaml");
        Reader configmapReader2 = ResourceUtils.read("skywalking-group-dynamic-configmap.example-serviceA.yaml");
        Reader configmapReader3 = ResourceUtils.read("skywalking-group-dynamic-configmap.example-serviceB.yaml");
        Map<String, Map<String, String>> configmapMap1 = yaml.loadAs(configmapReader1, Map.class);
        Map<String, Map<String, String>> configmapMap2 = yaml.loadAs(configmapReader2, Map.class);
        Map<String, Map<String, String>> configmapMap3 = yaml.loadAs(configmapReader3, Map.class);

        Map<String, String> configMapData = new HashMap<>();
        configMapData.putAll(configmapMap1.get("data"));
        configMapData.putAll(configmapMap2.get("data"));
        configMapData.putAll(configmapMap3.get("data"));

        return configMapData;
    }
}
