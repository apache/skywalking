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

package org.apache.skywalking.oap.server.admin.uimanagement;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import org.apache.skywalking.oap.server.admin.server.module.AdminServerModule;
import org.apache.skywalking.oap.server.admin.uimanagement.handler.UIManagementRestHandler;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class UIManagementModuleProvider extends ModuleProvider {
    public static final String NAME = "default";

    private UIManagementModuleConfig config;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return UIManagementModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<UIManagementModuleConfig>() {
            @Override
            public Class<UIManagementModuleConfig> type() {
                return UIManagementModuleConfig.class;
            }

            @Override
            public void onInitialized(final UIManagementModuleConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        final HTTPHandlerRegister adminRegister = getManager().find(AdminServerModule.NAME)
                                                              .provider()
                                                              .getService(HTTPHandlerRegister.class);
        adminRegister.addHandler(
            new UIManagementRestHandler(getManager()),
            Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT)
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            StorageModule.NAME,
            AdminServerModule.NAME,
        };
    }
}
