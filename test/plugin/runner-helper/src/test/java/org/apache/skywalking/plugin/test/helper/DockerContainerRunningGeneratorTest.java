/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.plugin.test.helper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DockerContainerRunningGeneratorTest {

    private DockerContainerRunningGenerator generator;

    @Mock
    private IConfiguration configuration;

    @Before
    public void setUp() {
        generator = new DockerContainerRunningGenerator();
        when(configuration.agentHome()).thenReturn("/agent/path");
        when(configuration.dockerImageName()).thenReturn("skyapm/agent-tomcat");
        when(configuration.entryService()).thenReturn("http://localhost:8080/entryService");
        when(configuration.healthCheck()).thenReturn("http://localhost:8080/healthCheck");
        when(configuration.scenarioVersion()).thenReturn("4.3.2");
        when(configuration.scenarioHome()).thenReturn("./scenario");
        when(configuration.scenarioName()).thenReturn("test");
    }

    @Test
    public void testGenerateDockerContainerStartScript() {
        String script = generator.runningScript(configuration);
        assertNotNull(script);
    }

}
