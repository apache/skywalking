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

package org.apache.skywalking.apm.collector.agent.jetty.provider.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServletHandler.class);

    private final IApplicationIDService applicationIDService;
    private Gson gson = new Gson();
    private static final String APPLICATION_CODE = "c";
    private static final String APPLICATION_ID = "i";

    public ApplicationRegisterServletHandler(ModuleManager moduleManager) {
        this.applicationIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IApplicationIDService.class);
    }

    @Override public String pathSpec() {
        return "/application/register";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonArray responseArray = new JsonArray();
        try {
            JsonArray applicationCodes = gson.fromJson(req.getReader(), JsonArray.class);
            for (int i = 0; i < applicationCodes.size(); i++) {
                String applicationCode = applicationCodes.get(i).getAsString();
                int applicationId = applicationIDService.getOrCreateForApplicationCode(applicationCode);
                JsonObject mapping = new JsonObject();
                mapping.addProperty(APPLICATION_CODE, applicationCode);
                mapping.addProperty(APPLICATION_ID, applicationId);
                responseArray.add(mapping);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
