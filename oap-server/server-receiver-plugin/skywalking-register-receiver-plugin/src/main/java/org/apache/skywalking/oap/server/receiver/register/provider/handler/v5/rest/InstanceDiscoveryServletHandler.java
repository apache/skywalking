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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5.rest;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.*;
import org.slf4j.*;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.*;

/**
 * @author peng-yongsheng
 */
public class InstanceDiscoveryServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServletHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInventoryCache serviceInventoryCache;
    private final Gson gson = new Gson();

    private static final String APPLICATION_ID = "ai";
    private static final String AGENT_UUID = "au";
    private static final String REGISTER_TIME = "rt";
    private static final String INSTANCE_ID = "ii";
    private static final String OS_INFO = "oi";

    public InstanceDiscoveryServletHandler(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
    }

    @Override public String pathSpec() {
        return "/instance/register";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonObject responseJson = new JsonObject();
        try {
            JsonObject instance = gson.fromJson(req.getReader(), JsonObject.class);
            int applicationId = instance.get(APPLICATION_ID).getAsInt();
            String agentUUID = instance.get(AGENT_UUID).getAsString();
            long registerTime = instance.get(REGISTER_TIME).getAsLong();
            JsonObject osInfoJson = instance.get(OS_INFO).getAsJsonObject();

            List<String> ipv4sList = new ArrayList<>();
            JsonArray ipv4s = osInfoJson.get("ipv4s").getAsJsonArray();
            ipv4s.forEach(ipv4 -> ipv4sList.add(ipv4.getAsString()));

            ServiceInventory serviceInventory = serviceInventoryCache.get(applicationId);

            JsonObject instanceProperties = new JsonObject();
            instanceProperties.addProperty(ServiceInstanceInventory.PropertyUtil.HOST_NAME, osInfoJson.get("hostName").getAsString());
            instanceProperties.addProperty(ServiceInstanceInventory.PropertyUtil.OS_NAME, osInfoJson.get("osName").getAsString());
            instanceProperties.addProperty(ServiceInstanceInventory.PropertyUtil.PROCESS_NO, osInfoJson.get("processId").getAsInt() + "");
            instanceProperties.addProperty(ServiceInstanceInventory.PropertyUtil.IPV4S, ServiceInstanceInventory.PropertyUtil.ipv4sSerialize(ipv4sList));

            String instanceName = serviceInventory.getName();
            if (instanceProperties.has(PROCESS_NO)) {
                instanceName += "-pid:" + instanceProperties.get(PROCESS_NO).getAsString();
            }
            if (instanceProperties.has(HOST_NAME)) {
                instanceName += "@" + instanceProperties.get(HOST_NAME).getAsString();
            }

            int instanceId = serviceInstanceInventoryRegister.getOrCreate(applicationId, instanceName, agentUUID, registerTime, instanceProperties);
            responseJson.addProperty(APPLICATION_ID, applicationId);
            responseJson.addProperty(INSTANCE_ID, instanceId);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseJson;
    }
}
