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

package org.apache.skywalking.apm.plugin.logger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContextConfigDefaultTest {
    @Test
    public void testDefaultConfig() {
        ContextConfig config = ContextConfig.getLatestConfig();
        ContextConfig.LoggerConfig log4jConfig = config.getLog4jConfig();
        ContextConfig.LoggerConfig log4j2Config = config.getLog4j2Config();
        ContextConfig.LoggerConfig logbackConfig = config.getLogbackConfig();
        //test log4j configuration
        assertEquals("log4j", log4jConfig.getName());
        assertEquals("ERROR", log4jConfig.getLevel().toString());
        assertEquals("*", log4jConfig.getPackages().get(0));
        //test log4j2 configuration
        assertEquals("log4j2", log4j2Config.getName());
        assertEquals("ERROR", log4j2Config.getLevel().toString());
        assertEquals("*", log4j2Config.getPackages().get(0));
        //test logback configuration
        assertEquals("logback", logbackConfig.getName());
        assertEquals("ERROR", logbackConfig.getLevel().toString());
        assertEquals("*", logbackConfig.getPackages().get(0));
    }
}
