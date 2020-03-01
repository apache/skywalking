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
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.register.v2.ServiceInstance;
import org.apache.skywalking.apm.network.register.v2.ServiceInstances;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.HOST_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.IPV4S;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.LANGUAGE;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.OS_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.PROCESS_NO;

public class ServiceInstanceRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceRegisterServletHandler.class);
    private static final String INSTANCE_CUSTOMIZED_NAME_PREFIX = "NAME:";

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInventoryCache serviceInventoryCache;

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
        JsonArray jsonArray = new JsonArray();

        try {
            ServiceInstances.Builder builder = ServiceInstances.newBuilder();
            ProtoBufJsonUtils.fromJSON(getJsonBody(req), builder);
            List<ServiceInstance> serviceInstances = builder.build().getInstancesList();

            serviceInstances.forEach(instance -> {
                long time = instance.getTime();
                int serviceId = instance.getServiceId();
                String instanceUUID = instance.getInstanceUUID();

                JsonObject instanceProperties = new JsonObject();
                List<String> ipv4s = new ArrayList<>();

                for (KeyStringValuePair property : instance.getPropertiesList()) {
                    String key = property.getKey();
                    switch (key) {
                        case HOST_NAME:
                            instanceProperties.addProperty(HOST_NAME, property.getValue());
                            break;
                        case OS_NAME:
                            instanceProperties.addProperty(OS_NAME, property.getValue());
                            break;
                        case LANGUAGE:
                            instanceProperties.addProperty(LANGUAGE, property.getValue());
                            break;
                        case "ipv4":
                            ipv4s.add(property.getValue());
                            break;
                        case PROCESS_NO:
                            instanceProperties.addProperty(PROCESS_NO, property.getValue());
                            break;
                        default:
                            instanceProperties.addProperty(key, property.getValue());
                    }
                }
                instanceProperties.addProperty(IPV4S, ServiceInstanceInventory.PropertyUtil.ipv4sSerialize(ipv4s));

                String instanceName = null;
                if (instanceUUID.startsWith(INSTANCE_CUSTOMIZED_NAME_PREFIX)) {
                    instanceName = instanceUUID.substring(INSTANCE_CUSTOMIZED_NAME_PREFIX.length());
                }

                ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);

                if (instanceName == null) {
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
                jsonArray.add(responseJson);
            });

            return jsonArray;
        } catch (IOException e) {
            responseJson.addProperty("error", e.getMessage());
            logger.error(e.getMessage(), e);
        }
        return responseJson;
    }
}
