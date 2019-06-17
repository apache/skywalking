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

package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WriterFactoryTest {

    @Before
    public void setup() throws AgentPackageNotFoundException, ConfigNotFoundException {
        AgentPackagePath.getPath();
    }

    @Test
    public void testLoggerDirInRelative() throws AgentPackageNotFoundException {
        String logDir = "./logs";
        Config.Logging.DIR = logDir;
        Assert.assertEquals(Config.Logging.DIR, logDir);
        Assert.assertEquals(WriterFactory.getLogWriter().getClass(), FileWriter.class);
        Assert.assertTrue(WriterFactory.IS_INITIALIZED);
    }

    @Test
    public void testLoggerDirInAbsolute() throws AgentPackageNotFoundException {
        String logDir = "/logs";
        Config.Logging.DIR = logDir;
        Assert.assertEquals(WriterFactory.getLogWriter().getClass(), FileWriter.class);
        Assert.assertEquals(Config.Logging.DIR, AgentPackagePath.getPath() + logDir);
        Assert.assertTrue(WriterFactory.IS_INITIALIZED);
    }


    @Test
    public void testDirEmptySystemOut() throws AgentPackageNotFoundException {
        Config.Logging.DIR = "";
        IWriter writer = WriterFactory.getLogWriter();
        Assert.assertEquals(writer.getClass(), SystemOutWriter.class);
        Assert.assertTrue(WriterFactory.IS_INITIALIZED);
    }

    @Test
    public void testDirIsNullSystemOut() throws AgentPackageNotFoundException {
        Config.Logging.DIR = null;
        IWriter writer = WriterFactory.getLogWriter();
        Assert.assertEquals(writer.getClass(), SystemOutWriter.class);
        Assert.assertTrue(WriterFactory.IS_INITIALIZED);
    }

    @After
    public void cleanup() {
        WriterFactory.IS_INITIALIZED = false;
    }
}
