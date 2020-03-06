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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.register.v2.Service;
import org.apache.skywalking.apm.network.register.v2.Services;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegisterServletHandler.class);

    private final IServiceInventoryRegister serviceInventoryRegister;

    private static final String KEY = "key";
    private static final String VALUE = "value";

    public ServiceRegisterServletHandler(ModuleManager moduleManager) {
        serviceInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                .provider()
                                                .getService(IServiceInventoryRegister.class);
    }

    @Override
    public String pathSpec() {
        return "/v2/service/register";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonArray responseArray = new JsonArray();

        try {
            Services.Builder builder = Services.newBuilder();
            ProtoBufJsonUtils.fromJSON(getJsonBody(req), builder);
            List<Service> serviceList = builder.build().getServicesList();

            serviceList.forEach(service -> {
                int serviceId = serviceInventoryRegister.getOrCreate(service.getServiceName(),
                                                                     NodeType.fromRegisterServiceType(
                                                                         service.getType()), null
                );

                JsonObject mapping = new JsonObject();
                mapping.addProperty(KEY, service.getServiceName());
                mapping.addProperty(VALUE, serviceId);
                responseArray.add(mapping);
            });
        } catch (IOException e) {
            JsonObject mapping = new JsonObject();
            mapping.addProperty("error", e.getMessage());
            responseArray.add(mapping);
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
