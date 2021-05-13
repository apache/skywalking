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

package org.apache.skywalking.oap.server.configuration.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class NacosConfigWatcherRegisterTest {
    @Test
    public void shouldReadConfigs() throws NacosException {
        final String group = "skywalking";
        final String testKey1 = "receiver-trace.default.slowDBAccessThreshold";
        final String testVal1 = "test";
        final String testKey2 = "testKey";
        final String testVal2 = "testVal";

        final NacosServerSettings mockSettings = mock(NacosServerSettings.class);
        when(mockSettings.getGroup()).thenReturn(group);
        when(mockSettings.getNamespace()).thenReturn("");

        final NacosConfigWatcherRegister mockRegister = spy(new NacosConfigWatcherRegister(mockSettings));
        final ConfigService mockConfigService = mock(ConfigService.class);
        when(mockConfigService.getConfig(testKey1, group, 1000)).thenReturn(testVal1);
        when(mockConfigService.getConfig(testKey2, group, 1000)).thenReturn(testVal2);

        Whitebox.setInternalState(mockRegister, "configService", mockConfigService);

        final ConfigTable configTable = mockRegister.readConfig(Sets.newHashSet(testKey1, testKey2)).get();

        assertEquals(2, configTable.getItems().size());
        Map<String, String> kvs = new HashMap<>();
        for (ConfigTable.ConfigItem item : configTable.getItems()) {
            kvs.put(item.getName(), item.getValue());
        }
        assertEquals(testVal1, kvs.get(testKey1));
        assertEquals(testVal2, kvs.get(testKey2));
    }
}
