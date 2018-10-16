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
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceHeartBeatServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatServletHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final Gson gson = new Gson();

    private static final String INSTANCE_ID = "ii";
    private static final String HEARTBEAT_TIME = "ht";

    public InstanceHeartBeatServletHandler(ModuleManager moduleManager) {
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IServiceInstanceInventoryRegister.class);
    }

    @Override public String pathSpec() {
        return "/instance/heartbeat";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        JsonObject responseJson = new JsonObject();
        try {
            JsonObject heartBeat = gson.fromJson(req.getReader(), JsonObject.class);
            int instanceId = heartBeat.get(INSTANCE_ID).getAsInt();
            long heartBeatTime = heartBeat.get(HEARTBEAT_TIME).getAsLong();

            serviceInstanceInventoryRegister.heartbeat(instanceId, heartBeatTime);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseJson;
    }
}
