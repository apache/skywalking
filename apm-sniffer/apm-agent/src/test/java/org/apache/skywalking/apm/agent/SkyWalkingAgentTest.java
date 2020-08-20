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

package org.apache.skywalking.apm.agent;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.logging.core.JsonLogger;
import org.apache.skywalking.apm.agent.core.logging.core.PatternLogger;
import org.apache.skywalking.apm.agent.core.logging.core.ResolverType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SkyWalkingAgentTest {

    @Before
    public void setLoggerBackDefault() {
        System.clearProperty("logging.logger");
        Config.Logging.LOGGER = ResolverType.PATTERN;
    }

    @Test
    public void testDefaultLogger() {
        SkyWalkingAgent.configureLogger();
        ILog logger = LogManager.getLogger(SkyWalkingAgentTest.class);
        Assert.assertTrue(logger instanceof PatternLogger);
    }

    @Test
    public void givenLowerCaseEnumValue_testJsonLogger() {
        System.setProperty("logging.logger", "Json");
        SkyWalkingAgent.configureLogger();
        ILog logger = LogManager.getLogger(SkyWalkingAgentTest.class);
        Assert.assertTrue(logger instanceof JsonLogger);
    }

    @Test
    public void testJsonLogger() {
        System.setProperty("logging.logger", "JSON");
        SkyWalkingAgent.configureLogger();
        ILog logger = LogManager.getLogger(SkyWalkingAgentTest.class);
        Assert.assertTrue(logger instanceof JsonLogger);
    }
}
