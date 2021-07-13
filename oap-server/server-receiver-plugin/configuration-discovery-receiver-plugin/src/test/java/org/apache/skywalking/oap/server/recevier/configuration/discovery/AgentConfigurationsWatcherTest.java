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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

public class AgentConfigurationsWatcherTest {
    @Spy
    private AgentConfigurationsWatcher agentConfigurationsWatcher = new AgentConfigurationsWatcher(null);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testConfigModifyEvent() throws IOException {
        AgentConfigurationsTable agentConfigurationsTable = Whitebox.getInternalState(
            agentConfigurationsWatcher, "agentConfigurationsTable");
        assertTrue(agentConfigurationsTable.getAgentConfigurationsCache().isEmpty());

        Reader reader = ResourceUtils.read("agent-dynamic-configuration.yml");
        char[] chars = new char[1024 * 1024];
        int length = reader.read(chars);

        agentConfigurationsWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
            new String(chars, 0, length),
            ConfigChangeWatcher.EventType.MODIFY
        ));

        AgentConfigurationsTable modifyAgentConfigurationsTable = Whitebox.getInternalState(
            agentConfigurationsWatcher, "agentConfigurationsTable");
        Map<String, AgentConfigurations> configurationCache = modifyAgentConfigurationsTable.getAgentConfigurationsCache();
        Assert.assertEquals(2, configurationCache.size());
        AgentConfigurations agentConfigurations0 = configurationCache.get("serviceA");
        Assert.assertEquals("serviceA", agentConfigurations0.getService());
        Assert.assertEquals(2, agentConfigurations0.getConfiguration().size());
        Assert.assertEquals("1000", agentConfigurations0.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", agentConfigurations0.getConfiguration().get("trace.ignore_path"));
        Assert.assertEquals(
            "92670f1ccbdee60e14ffc054d70a5cf3f93f6b5fb1adb83b10bea4fec79b96e7bc5e7b188e231428853721ded42ec756663947316065617f3cfdf51d6dfc8da6",
            agentConfigurations0.getUuid()
        );

        AgentConfigurations agentConfigurations1 = configurationCache.get("serviceB");
        Assert.assertEquals("serviceB", agentConfigurations1.getService());
        Assert.assertEquals(2, agentConfigurations1.getConfiguration().size());
        Assert.assertEquals("1000", agentConfigurations1.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", agentConfigurations1.getConfiguration().get("trace.ignore_path"));
        Assert.assertEquals(
            "92670f1ccbdee60e14ffc054d70a5cf3f93f6b5fb1adb83b10bea4fec79b96e7bc5e7b188e231428853721ded42ec756663947316065617f3cfdf51d6dfc8da6",
            agentConfigurations0.getUuid()
        );
    }

    @Test
    public void testConfigDeleteEvent() throws IOException {
        Reader reader = ResourceUtils.read("agent-dynamic-configuration.yml");
        agentConfigurationsWatcher = spy(new AgentConfigurationsWatcher(null));

        Whitebox.setInternalState(
            agentConfigurationsWatcher, "agentConfigurationsTable",
            new AgentConfigurationsReader(reader).readAgentConfigurationsTable()
        );

        agentConfigurationsWatcher.notify(
            new ConfigChangeWatcher.ConfigChangeEvent("whatever", ConfigChangeWatcher.EventType.DELETE));

        AgentConfigurationsTable agentConfigurationsTable = Whitebox.getInternalState(
            agentConfigurationsWatcher, "agentConfigurationsTable");
        Map<String, AgentConfigurations> configurationCache = agentConfigurationsTable.getAgentConfigurationsCache();

        Assert.assertEquals(0, configurationCache.size());
        AgentConfigurations agentConfigurations0 = configurationCache.get("serviceA");
        AgentConfigurations agentConfigurations1 = configurationCache.get("serviceB");

        Assert.assertNull(null, agentConfigurations0);
        Assert.assertNull(null, agentConfigurations1);
    }
}