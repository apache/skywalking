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

package org.apache.skywalking.aop.server.receiver.mesh;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

public class MeshReceiverProvider extends ModuleProvider {
    private MeshModuleConfig config;

    public MeshReceiverProvider() {
        config = new MeshModuleConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return MeshReceiverModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        MeshDataBufferFileCache cache = new MeshDataBufferFileCache(config);
        try {
            cache.start();
            TelemetryDataDispatcher.setCache(cache, getManager());
        } catch (IOException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
        CoreRegisterLinker.setModuleManager(getManager());
        GRPCHandlerRegister service = getManager().find(CoreModule.NAME).provider().getService(GRPCHandlerRegister.class);
        service.addHandler(new MeshGRPCHandler());
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
