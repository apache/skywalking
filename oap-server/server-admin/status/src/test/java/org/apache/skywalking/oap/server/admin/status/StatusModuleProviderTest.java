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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the dual-bind register-resolution helpers on StatusModuleProvider.
 * Handler instantiation itself is exercised end-to-end by the e2e suite —
 * unit-mocking every {@code manager.find(...)} chain inside each handler's
 * constructor would test the upstream services more than the registration logic.
 */
@ExtendWith(MockitoExtension.class)
class StatusModuleProviderTest {

    @Mock
    private ModuleManager moduleManager;

    @Mock
    private ModuleProviderHolder coreHolder;

    @Mock
    private ModuleProviderHolder adminHolder;

    @Mock
    private HTTPHandlerRegister publicRegister;

    @Mock
    private HTTPHandlerRegister adminRegister;

    private StatusModuleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StatusModuleProvider();
        provider.setManager(moduleManager);
    }

    @Test
    void publicRestRegisterResolvesViaCoreModule() {
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreHolder.provider()).thenReturn(stubProviderExposing(publicRegister));

        final HTTPHandlerRegister resolved = provider.publicRestRegister();

        assertSame(publicRegister, resolved);
        verify(moduleManager).find(CoreModule.NAME);
    }

    @Test
    void adminRestRegisterReturnsNullWhenAdminServerNotLoaded() {
        when(moduleManager.has(AdminServerModule.NAME)).thenReturn(false);

        assertNull(provider.adminRestRegisterOrNull());
        // Critical: must NOT touch find() if has() said no. AdminServerModule is
        // intentionally absent from requiredModules() so admin-server can stay
        // off without breaking status's public-REST binding for skywalking-ui.
        verify(moduleManager, never()).find(AdminServerModule.NAME);
    }

    @Test
    void adminRestRegisterResolvesAdminRegisterWhenAdminServerLoaded() {
        when(moduleManager.has(AdminServerModule.NAME)).thenReturn(true);
        when(moduleManager.find(AdminServerModule.NAME)).thenReturn(adminHolder);
        when(adminHolder.provider()).thenReturn(stubProviderExposing(adminRegister));

        final HTTPHandlerRegister resolved = provider.adminRestRegisterOrNull();

        assertNotNull(resolved);
        assertSame(adminRegister, resolved);
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
