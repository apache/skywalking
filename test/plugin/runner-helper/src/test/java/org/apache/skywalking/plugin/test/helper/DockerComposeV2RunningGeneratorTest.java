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

import java.io.File;
import java.io.FileNotFoundException;
import org.apache.skywalking.plugin.test.helper.exception.ConfigureFileNotFoundException;
import org.apache.skywalking.plugin.test.helper.exception.GenerateFailedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DockerComposeV2RunningGeneratorTest {
    private DockerComposeRunningGenerator dockerComposeRunningGenerator;

    private IConfiguration configuration;

    public static final String TARGET_DIR = DockerComposeV2RunningGeneratorTest.class.getResource("/").getFile();

    @Before
    public void setUp() throws FileNotFoundException, ConfigureFileNotFoundException {
        dockerComposeRunningGenerator = new DockerComposeRunningGenerator();

        System.setProperty("configure.file", TARGET_DIR + "configuration-test.yml");
        System.setProperty("scenario.home", "/solrj-scenario");
        System.setProperty("scenario.name", "solrj-scenario");
        System.setProperty("scenario.version", "7.7.1");
        System.setProperty("agent.dir", "/usr/local/skywalking-agent/");
        System.setProperty("output.dir", TARGET_DIR);

        configuration = new ConfigurationImpl();
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
