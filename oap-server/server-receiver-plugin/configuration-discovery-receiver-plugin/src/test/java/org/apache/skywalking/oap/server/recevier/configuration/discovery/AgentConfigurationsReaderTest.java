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

import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class AgentConfigurationsReaderTest {
    @Test
    public void testReadAgentConfigurations() {
        AgentConfigurationsReader reader = new AgentConfigurationsReader(
            this.getClass().getClassLoader().getResourceAsStream("agent-dynamic-configuration.yml"));

        Map<String, AgentConfigurations> configurationCache = reader.readAgentConfigurations();
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
}
