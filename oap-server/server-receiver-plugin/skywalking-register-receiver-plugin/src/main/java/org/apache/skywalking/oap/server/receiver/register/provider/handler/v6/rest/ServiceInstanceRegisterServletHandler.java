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
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.HOST_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.PROCESS_NO;

public class ServiceInstanceRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceRegisterServletHandler.class);
    private static final String INSTANCE_CUSTOMIZED_NAME_PREFIX = "NAME:";

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInventoryCache serviceInventoryCache;
    private final Gson gson = new Gson();

    private static final String INSTANCES = "instances";
    private static final String TIME = "time";
    private static final String SERVICE_ID = "serviceId";
    private static final String INSTANCE_UUID = "instanceUUID";
    private static final String PROPERTIES = "properties";
    private static final String KEY = "key";
    private static final String VALUE = "value";

    public ServiceInstanceRegisterServletHandler(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(
            IServiceInstanceInventoryRegister.class);
    }

    @Override
    public String pathSpec() {
        return "/v2/instance/register";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonObject responseJson = new JsonObject();
        try {
            JsonObject jsonObject = gson.fromJson(req.getReader(), JsonObject.class);
            JsonArray instances = jsonObject.getAsJsonArray(INSTANCES);
            instances.forEach(instanceObj -> {
                JsonObject instance = instanceObj.getAsJsonObject();
                long time = instance.get(TIME).getAsLong();
                int serviceId = instance.get(SERVICE_ID).getAsInt();
                String instanceUUID = instance.get(INSTANCE_UUID).getAsString();
                JsonArray properties = new JsonArray();
                JsonObject instanceProperties = new JsonObject();
                if (instance.has(PROPERTIES)) {
                    properties = instance.get(PROPERTIES).getAsJsonArray();
                }

                properties.forEach(property -> {
                    JsonObject prop = property.getAsJsonObject();
                    instanceProperties.addProperty(prop.get(KEY).getAsString(), prop.get(VALUE).getAsString());
                });

                String instanceName = null;
                if (instanceUUID.startsWith(INSTANCE_CUSTOMIZED_NAME_PREFIX)) {
                    instanceName = instanceUUID.substring(INSTANCE_CUSTOMIZED_NAME_PREFIX.length());
                }

                ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);

                if (instanceName == null) {
                    /**
                     * After 7.0.0, only active this naming rule when instance name has not been set in UUID parameter.
                     */
                    instanceName = serviceInventory.getName();
                    if (instanceProperties.has(PROCESS_NO)) {
                        instanceName += "-pid:" + instanceProperties.get(PROCESS_NO).getAsString();
                    }
                    if (instanceProperties.has(HOST_NAME)) {
                        instanceName += "@" + instanceProperties.get(HOST_NAME).getAsString();
                    }
                }

                int instanceId = serviceInstanceInventoryRegister.getOrCreate(
                    serviceId, instanceName, instanceUUID, time, instanceProperties);

                responseJson.addProperty(KEY, instanceUUID);
                responseJson.addProperty(VALUE, instanceId);
            });

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseJson;
    }
}
