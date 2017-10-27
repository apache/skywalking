/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.server.manager.grpc;

import java.util.Properties;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.server.manager.ServerManagerModule;
import org.skywalking.apm.collector.server.manager.grpc.service.GRPCServerService;
import org.skywalking.apm.collector.server.manager.service.GRPCServerManagerService;

/**
 * @author peng-yongsheng
 */
public class ServerManagerModuleGRPCProvider extends ModuleProvider {

    @Override public String name() {
        return "Google_RPC";
    }

    @Override public Class<? extends Module> module() {
        return ServerManagerModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(GRPCServerManagerService.class, new GRPCServerService());
    }

    @Override public void init(Properties config) throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
