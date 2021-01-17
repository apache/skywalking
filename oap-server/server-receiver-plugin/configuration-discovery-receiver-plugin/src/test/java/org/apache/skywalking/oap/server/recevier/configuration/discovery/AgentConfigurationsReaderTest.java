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
    public void testReadRules() {
        AgentConfigurationsReader reader = new AgentConfigurationsReader(
            this.getClass().getClassLoader().getResourceAsStream("configurationRules.yml"));

        AgentConfigurations agentConfigurations = reader.readRules();
        Map<String, ServiceConfiguration> rules = agentConfigurations.getRules();
        Assert.assertEquals(2, rules.size());
        ServiceConfiguration serviceConfigurationProvider = rules.get("serviceA");
        Assert.assertEquals("serviceA", serviceConfigurationProvider.getService());
        Assert.assertEquals(2, serviceConfigurationProvider.getConfiguration().size());
        Assert.assertEquals("1000", serviceConfigurationProvider.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", serviceConfigurationProvider.getConfiguration().get("trace.ignore_path"));

        ServiceConfiguration serviceConfigurationConsumer = rules.get("serviceB");
        Assert.assertEquals("serviceB", serviceConfigurationConsumer.getService());
        Assert.assertEquals(2, serviceConfigurationConsumer.getConfiguration().size());
        Assert.assertEquals("1000", serviceConfigurationConsumer.getConfiguration().get("trace.sample_rate"));
        Assert.assertEquals(
            "/api/seller/seller/*", serviceConfigurationConsumer.getConfiguration().get("trace.ignore_path"));
    }
}
