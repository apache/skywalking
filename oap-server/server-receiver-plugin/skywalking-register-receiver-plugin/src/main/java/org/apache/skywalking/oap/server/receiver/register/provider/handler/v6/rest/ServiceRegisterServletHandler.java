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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegisterServletHandler.class);

    private final IServiceInventoryRegister serviceInventoryRegister;
    private final Gson gson = new Gson();
    private static final String SERVICE_NAME = "service_name";
    private static final String SERVICE_ID = "service_id";

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
            JsonArray serviceCodes = gson.fromJson(req.getReader(), JsonArray.class);
            for (int i = 0; i < serviceCodes.size(); i++) {
                JsonObject service = serviceCodes.get(i).getAsJsonObject();
                String serviceCode = service.get(SERVICE_NAME).getAsString();
                int serviceId = serviceInventoryRegister.getOrCreate(serviceCode, null);
                JsonObject mapping = new JsonObject();
                mapping.addProperty(SERVICE_NAME, serviceCode);
                mapping.addProperty(SERVICE_ID, serviceId);
                responseArray.add(mapping);
                //
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
