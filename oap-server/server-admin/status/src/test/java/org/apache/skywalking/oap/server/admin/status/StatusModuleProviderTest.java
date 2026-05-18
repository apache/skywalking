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

package org.apache.skywalking.oap.server.admin.status;

import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.admin.server.module.AdminServerModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifies the register-resolution helpers on StatusModuleProvider. Status is
 * an admin-host module — handlers mount on the admin-server REST host — with
 * the single exception of {@code /status/config/ttl}, which is also bound on
 * the public REST host for baseline-predictor.
 */
@ExtendWith(MockitoExtension.class)
class StatusModuleProviderTest {

    @Mock
    private ModuleManager moduleManager;

    @Mock
    private ModuleProviderHolder adminHolder;

    @Mock
    private HTTPHandlerRegister adminRegister;

    @Mock
    private ModuleProviderHolder coreHolder;

    @Mock
    private HTTPHandlerRegister publicRegister;

    private StatusModuleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StatusModuleProvider();
        provider.setManager(moduleManager);
    }

    @Test
    void adminRestRegisterResolvesViaAdminServerModule() {
        when(moduleManager.find(AdminServerModule.NAME)).thenReturn(adminHolder);
        when(adminHolder.provider()).thenReturn(stubProviderExposing(adminRegister));

        final HTTPHandlerRegister resolved = provider.adminRestRegister();

        assertSame(adminRegister, resolved);
    }

    @Test
    void publicRestRegisterResolvesViaCoreModule() {
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreHolder.provider()).thenReturn(stubProviderExposing(publicRegister));

        final HTTPHandlerRegister resolved = provider.publicRestRegister();

        assertSame(publicRegister, resolved,
                   "/status/config/ttl is dual-bound — public binding must resolve via CoreModule");
    }

    @Test
    void requiredModulesDeclaresCoreAndAdminServer() {
        final List<String> required = Arrays.asList(provider.requiredModules());
        assertTrue(required.contains(CoreModule.NAME),
                   "CoreModule must remain in requiredModules() for ID resolution / metadata");
        assertTrue(required.contains(AdminServerModule.NAME),
                   "AdminServerModule must be in requiredModules() because handlers register on the admin REST host");
    }

    /**
     * Tiny adapter so {@code holder.provider().getService(HTTPHandlerRegister.class)}
     * resolves to the supplied register without us having to mock every step
     * of the chain in each test.
     */
    private static ModuleProvider stubProviderExposing(final HTTPHandlerRegister register) {
        return new ModuleProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public Class<? extends ModuleDefine> module() {
                return null;
            }

            @Override
            public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
                return null;
            }

            @Override
            public void prepare() {
            }

            @Override
            public void start() {
            }

            @Override
            public void notifyAfterCompleted() {
            }

            @Override
            public String[] requiredModules() {
                return new String[0];
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends Service> T getService(final Class<T> serviceType) {
                if (HTTPHandlerRegister.class.equals(serviceType)) {
                    return (T) register;
                }
                return null;
            }
        };
    }
}
