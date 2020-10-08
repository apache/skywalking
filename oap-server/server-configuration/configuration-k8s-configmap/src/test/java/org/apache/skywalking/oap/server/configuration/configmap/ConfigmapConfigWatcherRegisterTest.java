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

import io.kubernetes.client.openapi.models.V1ConfigMap;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
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
    public void readConfigWhenInformerNotwork() throws Exception {
        PowerMockito.doReturn(Optional.empty()).when(informer).configMap();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("key1");
        }});

        Assert.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();
        Assert.assertEquals(configTable.getItems().size(), 0);
    }

    @Test
    public void readConfigWhenInformerWork() throws Exception {
        Reader configmapReader = ResourceUtils.read("skywalking-dynamic-configmap.example.yaml");
        Map<String, Map<String, String>> configmapMap = yaml.loadAs(configmapReader, Map.class);
        V1ConfigMap v1ConfigMap = new V1ConfigMap();
        v1ConfigMap.data(configmapMap.get("data"));
        PowerMockito.doReturn(Optional.of(v1ConfigMap)).when(informer).configMap();
        Optional<ConfigTable> optionalConfigTable = register.readConfig(new HashSet<String>() {{
            add("receiver-trace.default.slowDBAccessThreshold");
            add("alarm.default.alarm-settings");
            add("core.default.apdexThreshold");
            add("receiver-trace.default.uninstrumentedGateways");
        }});
        Assert.assertTrue(optionalConfigTable.isPresent());
        ConfigTable configTable = optionalConfigTable.get();

        List<String> list = configTable.getItems().stream()
                                       .map(ConfigTable.ConfigItem::getValue)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toList());
        Assert.assertEquals(list.size(), 4);
    }
}
