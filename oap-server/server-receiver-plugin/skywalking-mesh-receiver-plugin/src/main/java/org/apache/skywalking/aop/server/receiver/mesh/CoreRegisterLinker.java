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
    private static volatile ModuleManager MODULE_MANAGER;
    private static volatile IServiceInventoryRegister SERVICE_INVENTORY_REGISTER;
    private static volatile IServiceInstanceInventoryRegister SERVICE_INSTANCE_INVENTORY_REGISTER;
    private static volatile IEndpointInventoryRegister ENDPOINT_INVENTORY_REGISTER;

    public static void setModuleManager(ModuleManager moduleManager) {
        CoreRegisterLinker.MODULE_MANAGER = moduleManager;
    }

    public static IServiceInventoryRegister getServiceInventoryRegister() {
        if (SERVICE_INVENTORY_REGISTER == null) {
            SERVICE_INVENTORY_REGISTER = MODULE_MANAGER.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        }
        return SERVICE_INVENTORY_REGISTER;
    }

    public static IServiceInstanceInventoryRegister getServiceInstanceInventoryRegister() {
        if (SERVICE_INSTANCE_INVENTORY_REGISTER == null) {
            SERVICE_INSTANCE_INVENTORY_REGISTER = MODULE_MANAGER.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        }
        return SERVICE_INSTANCE_INVENTORY_REGISTER;
    }

    public static IEndpointInventoryRegister getEndpointInventoryRegister() {
        if (ENDPOINT_INVENTORY_REGISTER == null) {
            ENDPOINT_INVENTORY_REGISTER = MODULE_MANAGER.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
        }
        return ENDPOINT_INVENTORY_REGISTER;
    }

}
