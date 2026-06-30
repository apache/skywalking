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

package org.apache.skywalking.oap.server.core.status;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.oap.server.core.status.ServerStatusService.ConfigList;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

public class ServerStatusServiceTest {
    private static final String KEYWORDS = "user,password";

    @Test
    public void shouldMergeAndMaskConfigDumpExtensions() throws Exception {
        ServerStatusService service = new ServerStatusService(mock(ModuleManager.class));
        seedBootingConfigurations(service);

        service.registerConfigDumpExtension(() -> Map.of(
            "storage.banyandb.global.targets", "127.0.0.1:17912",
            "storage.banyandb.global.user", "admin",
            "storage.banyandb.global.password", "s3cret",
            "storage.banyandb.metricsMinute.ttl", "7"
        ));

        ConfigList dump = service.dumpBootingConfigurations(KEYWORDS);

        // Secrets masked by their field-name keyword, applied centrally to extension rows too.
        assertEquals("******", dump.get("storage.banyandb.global.user"));
        assertEquals("******", dump.get("storage.banyandb.global.password"));
        // Non-secret extension values pass through, merged into the same dump.
        assertEquals("127.0.0.1:17912", dump.get("storage.banyandb.global.targets"));
        assertEquals("7", dump.get("storage.banyandb.metricsMinute.ttl"));
        // The application.yml-derived rows are still present.
        assertEquals("12800", dump.get("core.default.restPort"));
    }

    @Test
    public void shouldNotLeakExtensionsBeforeBoot() {
        ServerStatusService service = new ServerStatusService(mock(ModuleManager.class));
        service.registerConfigDumpExtension(
            () -> Map.of("storage.banyandb.global.targets", "127.0.0.1:17912"));

        // configurations not set yet (pre-boot) -> dump is empty; extensions are not dumped early.
        assertFalse(service.dumpBootingConfigurations(KEYWORDS)
                           .containsKey("storage.banyandb.global.targets"));
    }

    private void seedBootingConfigurations(ServerStatusService service) throws Exception {
        ApplicationConfiguration appConfig = new ApplicationConfiguration();
        Properties props = new Properties();
        props.put("restPort", "12800");
        ApplicationConfiguration.ModuleConfiguration core = appConfig.addModule("core");
        core.addProviderConfiguration("default", props);

        Field field = ServerStatusService.class.getDeclaredField("configurations");
        field.setAccessible(true);
        field.set(service, List.of(core));
    }
}
