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

package org.apache.skywalking.oap.server.configuration.zookeeper.ut;

import com.google.common.collect.Sets;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.zookeeper.ZookeeperServerSettings;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ZookeeperConfigWatcherRegisterTestCase {
    @Test
    public void TestCase() throws Exception {
        final String nameSpace = "/default";
        final String key = "receiver-trace.default.slowDBAccessThreshold";
        final String value = "default:100,mongodb:50";

        final ZookeeperServerSettings mockSettings = mock(ZookeeperServerSettings.class);
        when(mockSettings.getNameSpace()).thenReturn(nameSpace);

        final MockZookeeperConfigWatcherRegister mockRegister = spy(new MockZookeeperConfigWatcherRegister(mockSettings));
        final PathChildrenCache mockPathChildrenCache = mock(PathChildrenCache.class);
        when(mockPathChildrenCache.getCurrentData(nameSpace + "/" + key)).thenReturn(new ChildData(nameSpace + "/" + key, null, value
            .getBytes()));

        Whitebox.setInternalState(mockRegister, "childrenCache", mockPathChildrenCache);

        final ConfigTable configTable = mockRegister.readConfig(Sets.newHashSet(key)).get();

        assertEquals(1, configTable.getItems().size());
        assertEquals(key, configTable.getItems().get(0).getName());
        assertEquals(value, configTable.getItems().get(0).getValue());
    }
}
