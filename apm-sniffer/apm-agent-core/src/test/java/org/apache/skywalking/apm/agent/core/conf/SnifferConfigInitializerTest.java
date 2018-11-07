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

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.junit.AfterClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SnifferConfigInitializerTest {

    @Test
    public void testLoadConfigFromJavaAgentDir() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.application_code", "testApp");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        System.setProperty("skywalking.logging.level", "info");
        SnifferConfigInitializer.initialize(null);
        assertThat(Config.Agent.APPLICATION_CODE, is("testApp"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @Test
    public void testLoadConfigFromAgentOptions() throws AgentPackageNotFoundException, ConfigNotFoundException {
        String agentOptions = "agent.application_code=testApp,collector.backend_service=127.0.0.1:8090,logging.level=info";
        SnifferConfigInitializer.initialize(agentOptions);
        assertThat(Config.Agent.APPLICATION_CODE, is("testApp"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.INFO));
    }

    @Test
    public void testConfigOverriding() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.application_code", "testAppFromSystem");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        String agentOptions = "agent.application_code=testAppFromAgentOptions,logging.level=debug";
        SnifferConfigInitializer.initialize(agentOptions);
        assertThat(Config.Agent.APPLICATION_CODE, is("testAppFromAgentOptions"));
        assertThat(Config.Collector.BACKEND_SERVICE, is("127.0.0.1:8090"));
        assertThat(Config.Logging.LEVEL, is(LogLevel.DEBUG));
    }

    @Test
    public void testAgentOptionsSeparator() throws AgentPackageNotFoundException, ConfigNotFoundException {
        System.setProperty("skywalking.agent.application_code", "testApp");
        System.setProperty("skywalking.collector.backend_service", "127.0.0.1:8090");
        String agentOptions = "agent.ignore_suffix='.jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg'";
        SnifferConfigInitializer.initialize(agentOptions);
        assertThat(Config.Agent.IGNORE_SUFFIX, is(".jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg"));
    }

    @AfterClass
    public static void clear() {
        Config.Logging.LEVEL = LogLevel.DEBUG;
    }
}
