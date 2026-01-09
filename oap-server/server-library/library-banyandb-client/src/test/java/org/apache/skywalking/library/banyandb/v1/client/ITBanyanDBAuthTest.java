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

package org.apache.skywalking.library.banyandb.v1.client;

import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.UnauthenticatedException;
import org.apache.skywalking.oap.server.library.it.ITVersions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class ITBanyanDBAuthTest extends BanyanDBClientTestCI {
    private static final String REGISTRY = "ghcr.io";
    private static final String IMAGE_NAME = "apache/skywalking-banyandb";
    private static final String TAG = ITVersions.get("SW_BANYANDB_COMMIT");

    private static final String IMAGE = REGISTRY + "/" + IMAGE_NAME + ":" + TAG;

    protected static final int GRPC_PORT = 17912;
    protected static final int HTTP_PORT = 17913;

    public static GenericContainer<?> DB;

    @BeforeAll
    public static void init() throws Exception {
        // Step 1: prepare config file with 0600 permissions
        Path tempConfigPath = Files.createTempFile("bydb_server_config", ".yaml");
        Files.write(
            tempConfigPath, Files.readAllBytes(
                Paths.get(ITBanyanDBAuthTest.class.getClassLoader().getResource("config.yaml").toURI()))
        );
        Files.setPosixFilePermissions(tempConfigPath, PosixFilePermissions.fromString("rw-------"));

        // Step 2: create container
        DB = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withCopyFileToContainer(
                MountableFile.forHostPath(tempConfigPath),
                "/tmp/bydb_server_config.yaml"
            )
            .withCommand(
                "standalone",
                "--auth-config-file", "/tmp/bydb_server_config.yaml"
            )
            .withExposedPorts(GRPC_PORT, HTTP_PORT)
            .waitingFor(Wait.forHttp("/api/healthz").forPort(HTTP_PORT));
        DB.start();
    }

    @Test
    public void testAuthWithCorrect() throws IOException {
        BanyanDBClient client = createClient("admin", "123456");
        client.connect();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // get api version
            client.getAPIVersion();
            // list all groups
            List<BanyandbCommon.Group> groupList = client.findGroups();
            assertEquals(0, groupList.size());
        });
        client.close();
    }

    @Test
    public void testAuthWithWrong() throws IOException {
        BanyanDBClient client = createClient("admin", "123456" + "wrong");
        client.connect();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThrows(UnauthenticatedException.class, client::getAPIVersion);
        });
        client.close();
    }

    private BanyanDBClient createClient(String username, String password) {
        Options options = new Options();
        options.setUsername(username);
        options.setPassword(password);
        String url = String.format("%s:%d", DB.getHost(), DB.getMappedPort(GRPC_PORT));
        return new BanyanDBClient(new String[] {url}, options);
    }
}