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
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.service.IEndpointInventoryRegister;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDiscoveryServiceHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryServiceHandler.class);

    private final IEndpointInventoryRegister inventoryService;
    private final Gson gson = new Gson();

    private static final String APPLICATION_ID = "ai";
    private static final String SERVICE_NAME = "sn";
    private static final String SRC_SPAN_TYPE = "st";
    private static final String SERVICE_ID = "si";
    private static final String ELEMENT = "el";

    public ServiceNameDiscoveryServiceHandler(ModuleManager moduleManager) {
        this.inventoryService = moduleManager.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
    }

    @Override public String pathSpec() {
        return "/servicename/discovery";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonArray responseArray = new JsonArray();
        try {
            JsonArray services = gson.fromJson(req.getReader(), JsonArray.class);
            for (JsonElement service : services) {
                int applicationId = service.getAsJsonObject().get(APPLICATION_ID).getAsInt();
                String serviceName = service.getAsJsonObject().get(SERVICE_NAME).getAsString();
                int srcSpanType = service.getAsJsonObject().get(SRC_SPAN_TYPE).getAsInt();

                SpanType spanType = SpanType.forNumber(srcSpanType);
                if (Objects.nonNull(spanType)) {
                    int serviceId = inventoryService.getOrCreate(applicationId, serviceName, DetectPoint.fromSpanType(spanType));
                    if (serviceId != 0) {
                        JsonObject responseJson = new JsonObject();
                        responseJson.addProperty(SERVICE_ID, serviceId);
                        responseJson.add(ELEMENT, service);
                        responseArray.add(responseJson);
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
