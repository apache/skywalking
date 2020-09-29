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

import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {
    SnifferConfigInitializer.class,
    AgentPackagePath.class
})
public class WriterFactoryTest {

    @Test
    public void alwaysReturnSystemLogWriteWithSetLoggingDir() {
        Config.Logging.OUTPUT = LogOutput.CONSOLE;
        PowerMockito.mockStatic(SnifferConfigInitializer.class);
        PowerMockito.mockStatic(AgentPackagePath.class);
        BDDMockito.given(SnifferConfigInitializer.isInitCompleted()).willReturn(true);
        BDDMockito.given(AgentPackagePath.isPathFound()).willReturn(true);

        assertTrue(SnifferConfigInitializer.isInitCompleted());
        assertTrue(AgentPackagePath.isPathFound());

        IWriter logWriter = WriterFactory.getLogWriter();
        assertTrue(logWriter instanceof SystemOutWriter);
    }

    @Test
    public void returnFileWriterWriteWithBlankLoggingDir() {
        Config.Logging.OUTPUT = LogOutput.FILE;
        PowerMockito.mockStatic(SnifferConfigInitializer.class);
        PowerMockito.mockStatic(AgentPackagePath.class);
        BDDMockito.given(SnifferConfigInitializer.isInitCompleted()).willReturn(true);
        BDDMockito.given(AgentPackagePath.isPathFound()).willReturn(true);

        assertTrue(SnifferConfigInitializer.isInitCompleted());
        assertTrue(AgentPackagePath.isPathFound());

        IWriter logWriter = WriterFactory.getLogWriter();
        assertTrue(logWriter instanceof FileWriter);
    }
}