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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AgentConfigurationsReaderTest {
    @Test
    public void testReadAgentConfigurations() {
        AgentConfigurationsReader reader = new AgentConfigurationsReader(
            this.getClass().getClassLoader().getResourceAsStream("agent-dynamic-configuration.yml"));

        Map<String, AgentConfigurations> configurationCache = reader.readAgentConfigurationsTable()
                                                                    .getAgentConfigurationsCache();
        Assertions.assertEquals(3, configurationCache.size());
        AgentConfigurations agentConfigurations0 = configurationCache.get("serviceA");
        Assertions.assertEquals("serviceA", agentConfigurations0.getService());
        Assertions.assertEquals(3, agentConfigurations0.getConfiguration().size());
        Assertions.assertEquals("1000", agentConfigurations0.getConfiguration().get("trace.sample_rate"));
        Assertions.assertEquals("10", agentConfigurations0.getConfiguration().get("agent.sample_n_per_3_secs"));
        Assertions.assertEquals(
            "/api/seller/seller/*", agentConfigurations0.getConfiguration().get("trace.ignore_path"));
        Assertions.assertEquals(
            "285ab9c676d0733aaf487720a98cb0c9864cfe77b2fe5a19cdb519b5b382137b6c580f25e6daefca43b812f60230f7b4159cd9376f2e6f53588446b31a4ad10e",
            agentConfigurations0.getUuid()
        );

        AgentConfigurations agentConfigurations1 = configurationCache.get("serviceB");
        Assertions.assertEquals("serviceB", agentConfigurations1.getService());
        Assertions.assertEquals(3, agentConfigurations1.getConfiguration().size());
        Assertions.assertEquals("1000", agentConfigurations1.getConfiguration().get("trace.sample_rate"));
        Assertions.assertEquals("10", agentConfigurations1.getConfiguration().get("agent.sample_n_per_3_secs"));
        Assertions.assertEquals(
            "/api/seller/seller/*", agentConfigurations1.getConfiguration().get("trace.ignore_path"));
        Assertions.assertEquals(
            "285ab9c676d0733aaf487720a98cb0c9864cfe77b2fe5a19cdb519b5b382137b6c580f25e6daefca43b812f60230f7b4159cd9376f2e6f53588446b31a4ad10e",
            agentConfigurations0.getUuid()
        );

        AgentConfigurations agentConfigurations2 = configurationCache.get("default-config");
        Assertions.assertEquals("default-config", agentConfigurations2.getService());
        Assertions.assertEquals(2, agentConfigurations2.getConfiguration().size());
        Assertions.assertEquals("2000", agentConfigurations2.getConfiguration().get("trace.sample_rate"));
        Assertions.assertEquals("10", agentConfigurations2.getConfiguration().get("agent.sample_n_per_3_secs"));
        Assertions.assertEquals(
                "a8da29ed936552b4cb8925cb6180bde7ef95655552b9e9d4a2ca51f71789529e9777beb248817e215b950c9ee3c41c53e3252b05c61d4d7e5000fd7f83dc4b54",
                agentConfigurations2.getUuid()
        );
    }
}
