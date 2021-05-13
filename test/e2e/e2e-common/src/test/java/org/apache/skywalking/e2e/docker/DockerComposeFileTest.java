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

package org.apache.skywalking.e2e.docker;

import org.apache.skywalking.e2e.utils.Yamls;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

class DockerComposeFileTest {
    private static DockerComposeFile COMPOSE_FILE_ONE = null;
    private static DockerComposeFile COMPOSE_FILE_TWO = null;

    @BeforeAll
    static void setUp() throws Exception {
        COMPOSE_FILE_ONE = Yamls.load("docker-compose.one.yml").as(DockerComposeFile.class);
        COMPOSE_FILE_TWO = Yamls.load("docker-compose.two.yml").as(DockerComposeFile.class);
    }

    @Test
    void getAllConfigInfo() throws IOException, InterruptedException, URISyntaxException {
        File composeOne = new File(DockerComposeFileTest.class
                .getClassLoader()
                .getResource("docker-compose.one.yml")
                .toURI());

        File composeTwo = new File(DockerComposeFileTest.class
                .getClassLoader()
                .getResource("docker-compose.two.yml")
                .toURI());

        List<String> filePathList = new ArrayList<>();
        filePathList.add(composeOne.getAbsolutePath());
        filePathList.add(composeTwo.getAbsolutePath());
        DockerComposeFile testFile = DockerComposeFile.getAllConfigInfo(filePathList);
        Assert.assertNotNull(testFile);
        Assert.assertNotNull(testFile.getServices());
        Assert.assertEquals(COMPOSE_FILE_ONE.getServices().size() + COMPOSE_FILE_TWO.getServices().size(),
                testFile.getServices().size());
    }

    @Test
    void getServiceExposedPort() {
        List<String> ports = COMPOSE_FILE_TWO.getServiceExposedPorts("oap");
        Assert.assertNotNull(ports);
        Assert.assertEquals(3, ports.size());
    }

    @Test
    void isExposedPort() {
        boolean result = COMPOSE_FILE_TWO.isExposedPort("oap", 5005);
        Assert.assertTrue(result);
        result = COMPOSE_FILE_TWO.isExposedPort("oap", 5006);
        Assert.assertFalse(result);
    }
}