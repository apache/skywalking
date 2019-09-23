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

import org.apache.skywalking.plugin.test.helper.exception.GenerateFailedException;
import org.apache.skywalking.plugin.test.helper.vo.CaseConfiguration;
import org.apache.skywalking.plugin.test.helper.vo.CaseIConfigurationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DockerComposeV2RunningGeneratorTest {
    private DockerComposeV2RunningGenerator dockerComposeRunningGenerator;
    private InputStream configurationFile;

    @Mock
    private IConfiguration configuration;

    public static final String TARGET_DIR = DockerComposeRunningGeneratorTest.class.getResource("/").getFile();

    @Before
    public void setUp() {
        dockerComposeRunningGenerator = new DockerComposeV2RunningGenerator();

        when(configuration.outputDir()).thenReturn(TARGET_DIR);
        when(configuration.agentHome()).thenReturn("/agent/path");
        when(configuration.dockerImageName()).thenReturn("skyapm/agent-tomcat");
        when(configuration.entryService()).thenReturn("http://localhost:8080/entryService");
        when(configuration.healthCheck()).thenReturn("http://localhost:8080/healthCheck");
        when(configuration.testFramework()).thenReturn("http");
        when(configuration.scenarioVersion()).thenReturn("4.3.2");

        when(configuration.scenarioName()).thenReturn("http");
        when(configuration.scenarioHome()).thenReturn("scenario_home");
        when(configuration.dockerContainerName()).thenReturn("docker_container_name");
//        when(configuration.serverAddr()).thenReturn("")


        configurationFile = CaseIConfigurationTest.class.getResourceAsStream("/configuration-test.yml");
        assertNotNull(configurationFile);
        when(configuration.caseConfiguration()).thenReturn(new Yaml().loadAs(configurationFile, CaseConfiguration.class));
    }

    @Test
    public void testGenerateDockerCompose() {
        String runningScript = dockerComposeRunningGenerator.runningScript(configuration);
        assertEquals(String.format("docker-compose -f %s/docker-compose.yml up", TARGET_DIR), runningScript);
    }

    @Test
    public void testGenerateAdditionalFile() throws GenerateFailedException {
        dockerComposeRunningGenerator.generateAdditionFiles(configuration);
        assertTrue(new File(TARGET_DIR, "docker-compose.yml").exists());
    }

    @After
    public void tearDown() {

    }
}
