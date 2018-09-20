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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.service.IEndpointInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author wusheng
 */
public class CoreRegisterLinker {
    private static volatile ModuleManager moduleManager;
    private static volatile IServiceInventoryRegister serviceInventoryRegister;
    private static volatile IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private static volatile IEndpointInventoryRegister endpointInventoryRegister;

    public static void setModuleManager(ModuleManager moduleManager) {
        CoreRegisterLinker.moduleManager = moduleManager;
    }

    public static IServiceInventoryRegister getServiceInventoryRegister() {
        if (serviceInventoryRegister == null) {
            serviceInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IServiceInventoryRegister.class);
        }
        return serviceInventoryRegister;
    }

    public static IServiceInstanceInventoryRegister getServiceInstanceInventoryRegister() {
        if (serviceInstanceInventoryRegister == null) {
            serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IServiceInstanceInventoryRegister.class);
        }
        return serviceInstanceInventoryRegister;
    }

    public static IEndpointInventoryRegister getEndpointInventoryRegister() {
        if (endpointInventoryRegister == null) {
            endpointInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IEndpointInventoryRegister.class);
        }
        return endpointInventoryRegister;
    }

}
