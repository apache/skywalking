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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the wiring of {@code secretsManagementFile} from bydb.yml onto the config bean. A silently
 * unbound key here would leave the credential watcher permanently disabled with no error to point at.
 */
public class BanyanDBSecretsConfigTest {
    private static final String SECRETS_FILE_PROP = "SW_STORAGE_BANYANDB_SECRETS_MANAGEMENT_FILE";

    @AfterEach
    public void clearProps() {
        System.clearProperty(SECRETS_FILE_PROP);
    }

    @Test
    public void shouldResolveSecretsManagementFileFromEnvironment() throws Exception {
        assertEquals("", newLoader().loadConfig().getGlobal().getSecretsManagementFile());

        System.setProperty(SECRETS_FILE_PROP, "/etc/skywalking/bydb-secrets.properties");
        assertEquals(
            "/etc/skywalking/bydb-secrets.properties",
            newLoader().loadConfig().getGlobal().getSecretsManagementFile());
    }

    private BanyanDBConfigLoader newLoader() {
        ModuleDefine moduleDefine = mock(ModuleDefine.class);
        ModuleProvider provider = mock(ModuleProvider.class);
        when(provider.name()).thenReturn("default");
        when(provider.getModule()).thenReturn(moduleDefine);
        return new BanyanDBConfigLoader(provider);
    }
}
