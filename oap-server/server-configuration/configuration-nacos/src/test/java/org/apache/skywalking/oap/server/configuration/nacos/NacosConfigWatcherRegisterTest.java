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

import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Sets;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author kezhenxu94
 */
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

        final NacosConfigWatcherRegister mockRegister = new NacosConfigWatcherRegister(mockSettings);

        mockRegister.onDataIdValueChanged(testKey1, testVal1);
        mockRegister.onDataIdValueChanged(testKey2, testVal2);

        final ConfigTable configTable = mockRegister.readConfig(Sets.newHashSet(testKey1, testKey2));

        assertEquals(2, configTable.getItems().size());
        Map<String, String> kvs = new HashMap<>();
        for (ConfigTable.ConfigItem item : configTable.getItems()) {
            kvs.put(item.getName(), item.getValue());
        }
        assertEquals(testVal1, kvs.get(testKey1));
        assertEquals(testVal2, kvs.get(testKey2));
    }
}
