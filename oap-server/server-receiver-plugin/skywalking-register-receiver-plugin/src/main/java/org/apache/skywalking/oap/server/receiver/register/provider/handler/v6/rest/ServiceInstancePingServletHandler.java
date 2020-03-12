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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v6.rest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.register.v2.ServiceInstancePingPkg;
import org.apache.skywalking.apm.network.trace.component.command.ServiceResetCommand;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceInstancePingServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancePingServletHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final CommandService commandService;
    private final Gson gson = new Gson();

    public ServiceInstancePingServletHandler(ModuleManager moduleManager) {
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(
            IServiceInstanceInventoryRegister.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(
            ServiceInstanceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(
            IServiceInventoryRegister.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
    }

    @Override
    public String pathSpec() {
        return "/v2/instance/heartbeat";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        JsonObject responseJson = new JsonObject();

        try {
            ServiceInstancePingPkg.Builder builder = ServiceInstancePingPkg.newBuilder();
            ProtoBufJsonUtils.fromJSON(getJsonBody(req), builder);
            ServiceInstancePingPkg instancePingPkg = builder.build();

            int serviceInstanceId = instancePingPkg.getServiceInstanceId();
            long heartBeatTime = instancePingPkg.getTime();
            String serviceInstanceUUID = instancePingPkg.getServiceInstanceUUID();
            serviceInstanceInventoryRegister.heartbeat(serviceInstanceId, heartBeatTime);

            ServiceInstanceInventory serviceInstanceInventory = serviceInstanceInventoryCache.get(serviceInstanceId);
            if (Objects.nonNull(serviceInstanceInventory)) {
                serviceInventoryRegister.heartbeat(serviceInstanceInventory.getServiceId(), heartBeatTime);
            } else {
                logger.warn(
                    "Can't found service by service instance id from cache, service instance id is: {}",
                    serviceInstanceId
                );

                final ServiceResetCommand resetCommand = commandService.newResetCommand(
                    serviceInstanceId, heartBeatTime, serviceInstanceUUID);
                final Command command = resetCommand.serialize().build();
                final Commands nextCommands = Commands.newBuilder().addCommands(command).build();
                return gson.fromJson(ProtoBufJsonUtils.toJSON(nextCommands), JsonElement.class);
            }

        } catch (IOException e) {
            responseJson.addProperty("error", e.getMessage());
            logger.error(e.getMessage(), e);
        }

        return responseJson;
    }
}
