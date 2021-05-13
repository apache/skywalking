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

package org.apache.skywalking.apm.agent.core.conf;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class SnifferConfigInitializerTest {
    /**
     * The EnvironmentVariables rule allows you to set environment variables for your test. All changes to environment
     * variables are reverted after the test.
     */
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables().set("AGENT_SERVICE_NAME", "testAppFromSystemEnv")
                                                                                       .set("AGENT_COLLECTOR_SERVER", "localhost:11111");

    @Test
    public void testLoadConfigFromJavaAgentDir() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.service_name", "testApp");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        System.setProperty("skywalking.logging.level", "info");
        SnifferConfigInitializer.initializeCoreConfig(null);
        assertThat(Config.Agent.SERVICE_NAME, is("testApp"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @Test
    public void testLoadConfigFromAgentOptions() throws AgentPackageNotFoundException, ConfigNotFoundException {
        String agentOptions = "agent.service_name=testApp,collector.backend_service=127.0.0.1:8090,logging.level=info";
        SnifferConfigInitializer.initializeCoreConfig(agentOptions);
        assertThat(Config.Agent.SERVICE_NAME, is("testApp"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @Test
    public void testConfigOverriding() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.service_name", "testAppFromSystem");
        System.setProperty("skywalking.agent.instance_properties[key1]", "value1");
        System.setProperty("skywalking.agent.instance_properties[key2]", "value2");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        String agentOptions = "agent.service_name=testAppFromAgentOptions,logging.level=debug";
        SnifferConfigInitializer.initializeCoreConfig(agentOptions);
        assertThat(Config.Agent.SERVICE_NAME, is("testAppFromAgentOptions"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.DEBUG));
        assertThat(Config.Agent.INSTANCE_PROPERTIES.get("key1"), is("value1"));
        assertThat(Config.Agent.INSTANCE_PROPERTIES.get("key2"), is("value2"));
    }

    @Test
    public void testConfigOverridingFromSystemEnv() throws IllegalAccessException {
        Properties properties = new Properties();
        properties.put("agent.service_name", "${AGENT_SERVICE_NAME:testAppFromSystem}");
        properties.put("collector.backend_service", "${AGENT_COLLECTOR_SERVER:127.0.0.1:8090}");
        properties.put("logging.level", "INFO");
        PropertyPlaceholderHelper placeholderHelper = PropertyPlaceholderHelper.INSTANCE;
        properties.put("agent.service_name", placeholderHelper.replacePlaceholders((String) properties.get("agent.service_name"), properties));
        properties.put("collector.backend_service", placeholderHelper.replacePlaceholders((String) properties.get("collector.backend_service"), properties));
        ConfigInitializer.initialize(properties, Config.class);
        assertThat(Config.Agent.SERVICE_NAME, is("testAppFromSystemEnv"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("localhost:11111"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @Test
    public void testAgentOptionsSeparator() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.service_name", "testApp");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        String agentOptions = "agent.ignore_suffix='.jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg'";
        SnifferConfigInitializer.initializeCoreConfig(agentOptions);
        assertThat(Config.Agent.IGNORE_SUFFIX, is(".jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg"));
    }

    @Test
    public void testAgentOptionsParser() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        String agentOptions = "agent.service_name=test=abc";
        try {
            SnifferConfigInitializer.initializeCoreConfig(agentOptions);
            fail("test=abc without quotes is not a valid value");
        } catch (ExceptionInInitializerError e) {
            // ignore
        }
        agentOptions = "agent.service_name='test=abc'";
        SnifferConfigInitializer.initializeCoreConfig(agentOptions);
        assertThat(Config.Agent.SERVICE_NAME, is("test=abc"));
    }

    @After
    public void clear() {
        Iterator<Map.Entry<Object, Object>> it = System.getProperties().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Object> entry = it.next();
            if (entry.getKey().toString().startsWith("skywalking.")) {
                it.remove();
            }
        }

        Config.Agent.SERVICE_NAME = "";
        Config.Logging.LEVEL = LogLevel.DEBUG;
    }
}
