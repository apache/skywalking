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
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

public class AgentConfigurationsWatcherTest {
    @Spy
    private AgentConfigurationsWatcher agentConfigurationsWatcher = new AgentConfigurationsWatcher(
        new HashMap<>(), null);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testConfigModifyEvent() throws IOException {
        assertTrue(agentConfigurationsWatcher.getActiveAgentConfigurationsCache().isEmpty());

        Reader reader = ResourceUtils.read("agent-dynamic-configuration.yml");
        char[] chars = new char[1024 * 1024];
        int length = reader.read(chars);

        agentConfigurationsWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
            new String(chars, 0, length),
            ConfigChangeWatcher.EventType.MODIFY
        ));

        Map<String, AgentConfigurations> configurationCache = agentConfigurationsWatcher.getActiveAgentConfigurationsCache();
        Assert.assertEquals(2, configurationCache.size());
        AgentConfigurations agentConfigurations0 = configurationCache.get("serviceA");
        Assert.assertEquals("serviceA", agentConfigurations0.getService());
        Assert.assertEquals(2, agentConfigurations0.getConfiguration().size());
        Assert.assertEquals("1000", agentConfigurations0.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", agentConfigurations0.getConfiguration().get("trace.ignore_path"));

        AgentConfigurations agentConfigurations1 = configurationCache.get("serviceB");
        Assert.assertEquals("serviceB", agentConfigurations1.getService());
        Assert.assertEquals(2, agentConfigurations1.getConfiguration().size());
        Assert.assertEquals("1000", agentConfigurations1.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", agentConfigurations1.getConfiguration().get("trace.ignore_path"));
    }

    @Test
    public void testConfigDeleteEvent() throws IOException {
        Reader reader = ResourceUtils.read("agent-dynamic-configuration.yml");
        agentConfigurationsWatcher = spy(
            new AgentConfigurationsWatcher(new AgentConfigurationsReader(reader).readAgentConfigurations(), null));

        agentConfigurationsWatcher.notify(
            new ConfigChangeWatcher.ConfigChangeEvent("whatever", ConfigChangeWatcher.EventType.DELETE));

        Map<String, AgentConfigurations> configurationCache = agentConfigurationsWatcher.getActiveAgentConfigurationsCache();
        Assert.assertEquals(0, configurationCache.size());
        AgentConfigurations agentConfigurations0 = configurationCache.get("serviceA");
        AgentConfigurations agentConfigurations1 = configurationCache.get("serviceB");

        Assert.assertNull(null, agentConfigurations0);
        Assert.assertNull(null, agentConfigurations1);
    }
}