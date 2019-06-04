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
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author kezhenxu94
 */
public class NacosConfigWatcherRegisterTest {
    @Test
    public void shouldReadConfigs() throws NacosException {
        final NacosConfigWatcherRegister mockRegister = mock(NacosConfigWatcherRegister.class);
        final ConfigService mockConfigService = mock(ConfigService.class);
        final NacosServerSettings mockSettings = mock(NacosServerSettings.class);

        when(mockSettings.getGroup()).thenReturn("skywalking");
        when(mockSettings.getTimeOutInMs()).thenReturn(3000L);
        when(mockSettings.getDataIds()).thenReturn(new String[] {
            "receiver-trace.default.slowDBAccessThreshold", "test"
        });
        when(mockConfigService.getConfig("receiver-trace.default.slowDBAccessThreshold", "skywalking", 3000L)).thenReturn("testValue1");
        when(mockConfigService.getConfig("test", "skywalking", 3000L)).thenReturn("testValue2");

        Whitebox.setInternalState(mockRegister, "configService", mockConfigService);
        Whitebox.setInternalState(mockRegister, "settings", mockSettings);

        when(mockRegister.readConfig()).thenCallRealMethod();

        final ConfigTable configTable = mockRegister.readConfig();

        assertEquals(2, configTable.getItems().size());
        assertEquals("receiver-trace.default.slowDBAccessThreshold", configTable.getItems().get(0).getName());
        assertEquals("testValue1", configTable.getItems().get(0).getValue());
        assertEquals("test", configTable.getItems().get(1).getName());
        assertEquals("testValue2", configTable.getItems().get(1).getValue());
    }
}
