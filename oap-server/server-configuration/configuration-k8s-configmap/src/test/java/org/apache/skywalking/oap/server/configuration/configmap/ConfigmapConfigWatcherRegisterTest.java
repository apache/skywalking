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

import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ConfigmapConfigWatcherRegisterTest {

    private ConfigmapConfigurationWatcherRegister register;

    private ConfigurationConfigmapInformer informer;

    private final Yaml yaml = new Yaml();

    @BeforeEach
    public void prepare() {
        ConfigmapConfigurationSettings settings = new ConfigmapConfigurationSettings();
        settings.setPeriod(60);
        informer = mock(ConfigurationConfigmapInformer.class);
        register = new ConfigmapConfigurationWatcherRegister(settings, informer);
    }

    @Test
    public void readConfigWhenConfigMapDataIsNull() {
        Map<String, String> configMapData = new HashMap<>();
        doReturn(configMapData).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assertions.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();
        Assertions.assertEquals(configTable.getItems().size(), 1);
        Assertions.assertEquals(configTable.getItems().get(0).getName(), "key1");
        Assertions.assertNull(configTable.getItems().get(0).getValue());
    }

    @Test
    public void readConfigWhenInformerNotwork() throws Exception {
        doReturn(new HashMap<>()).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assertions.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();
        Assertions.assertEquals(configTable.getItems().size(), 1);
        Assertions.assertEquals(configTable.getItems().get(0).getName(), "key1");
        Assertions.assertNull(configTable.getItems().get(0).getValue());
    }

    @Test
    public void readConfigWhenInformerWork() throws Exception {
        Map<String, String> configMapData = this.readMockConfigMapData();
        doReturn(configMapData).when(informer).configMapData();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("agent-analyzer.default.slowDBAccessThreshold");
            add("alarm.default.alarm-settings");
            add("core.default.apdexThreshold");
            add("agent-analyzer.default.uninstrumentedGateways");
        }});

        Assertions.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();

        List<String> list = configTable.getItems().stream()
                                       .map(ConfigTable.ConfigItem::getValue)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toList());
        Assertions.assertEquals(list.size(), 4);
    }

    @Test
    public void readGroupConfigWhenConfigMapDataIsNull() throws Exception {
        Map<String, String> configMapData = new HashMap<>();
        doReturn(configMapData).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assertions.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();
        Assertions.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "key1");
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 0);
    }

    @Test
    public void readGroupConfigWhenInformerNotwork() throws Exception {
        doReturn(new HashMap<>()).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assertions.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();
        Assertions.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "key1");
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 0);
    }

    @Test
    public void readGroupConfigWhenInformerWork() throws Exception {
        Map<String, String> configMapData = this.readMockConfigMapData();
        doReturn(configMapData).when(informer).configMapData();
        Optional<GroupConfigTable> optionalGroupConfigTable = register.readGroupConfig(new HashSet<String>() {{
            add("core.default.endpoint-name-grouping-openapi");
        }});

        Assertions.assertTrue(optionalGroupConfigTable.isPresent());
        GroupConfigTable groupConfigTable = optionalGroupConfigTable.get();

        Assertions.assertEquals(groupConfigTable.getGroupItems().size(), 1);
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getName(), "core.default.endpoint-name-grouping-openapi");
        Assertions.assertEquals(groupConfigTable.getGroupItems().get(0).getItems().size(), 3);
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
