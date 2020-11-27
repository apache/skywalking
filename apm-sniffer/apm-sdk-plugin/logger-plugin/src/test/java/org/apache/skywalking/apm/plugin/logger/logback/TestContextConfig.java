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

package org.apache.skywalking.apm.plugin.logger.logback;

import ch.qos.logback.classic.Level;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestContextConfig {
    @Test
    public void testGetInstance() {
//        ContextConfig config = ContextConfig.getInstance();
//        LoggerConfig logbakConfig = config.getLogbakConfig();
//        LoggerConfig log4j2Config = config.getLog4j2Config();
//
//        //test logbak
//        assertEquals(logbakConfig.getName(), "logbak");
//        assertEquals(logbakConfig.getExpression(), "Regular expression");
//        assertEquals(logbakConfig.getLevel(), Level.ERROR);
//        assertEquals(logbakConfig.getPackages().get(0).toString(), "*");
//        assertEquals(logbakConfig.getPattern(), "%msg%n");
//
//        //test log4j
//        assertEquals(log4j2Config.getName(), "log4j2");
//        assertEquals(log4j2Config.getExpression(), "Regular expression");
//        assertEquals(log4j2Config.getLevel(), Level.TRACE);
//        assertEquals(log4j2Config.getPattern(), "%date %level [%thread] %logger{10} [%file:%line] %msg%n");
//        assertEquals(log4j2Config.getPackages().get(0), "package1");
//        assertEquals(log4j2Config.getPackages().get(1), "package2");
    }
}
