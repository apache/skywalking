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
import java.util.List;

class DockerComposeFileTest {
    private static DockerComposeFile COMPOSE_FILE = null;

    @BeforeAll
    static void setUp() throws Exception {
        COMPOSE_FILE = Yamls.load("docker-compose.yml").as(DockerComposeFile.class);
    }

    @Test
    void getAllConfigInfo() throws IOException, InterruptedException, URISyntaxException {
        File file = new File(DockerComposeFileTest.class
                .getClassLoader()
                .getResource("docker-compose.yml")
                .toURI());
        DockerComposeFile testFile = DockerComposeFile.getAllConfigInfo(file.getAbsolutePath());
        Assert.assertNotNull(testFile);
        Assert.assertNotNull(testFile.getServices());
        Assert.assertEquals(COMPOSE_FILE.getServices().size(), testFile.getServices().size());
        Assert.assertEquals(COMPOSE_FILE.getVersion(), testFile.getVersion());
    }

    @Test
    void getServiceExposedPort() {
        List<String> ports = COMPOSE_FILE.getServiceExposedPorts("oap");
        Assert.assertNotNull(ports);
        Assert.assertEquals(3, ports.size());
    }

    @Test
    void isExposedPort() {
        boolean result = COMPOSE_FILE.isExposedPort("oap", 5005);
        Assert.assertTrue(result);
        result = COMPOSE_FILE.isExposedPort("oap", 5006);
        Assert.assertFalse(result);
    }
}