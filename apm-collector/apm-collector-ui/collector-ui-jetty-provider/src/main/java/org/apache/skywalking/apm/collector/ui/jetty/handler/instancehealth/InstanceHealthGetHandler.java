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


package org.apache.skywalking.apm.collector.ui.jetty.handler.instancehealth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.InstanceHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceHealthGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/health/applicationId";
    }

    private final InstanceHealthService service;

    public InstanceHealthGetHandler(ModuleManager moduleManager) {
        this.service = new InstanceHealthService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timeBucketStr = req.getParameter("timeBucket");
        String[] applicationIdsStr = req.getParameterValues("applicationIds");
        logger.debug("instance health get timeBucket: {}, applicationIdsStr: {}", timeBucketStr, applicationIdsStr);

        long timeBucket;
        try {
            timeBucket = Long.parseLong(timeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timestamp must be long");
        }

        int[] applicationIds = new int[applicationIdsStr.length];
        for (int i = 0; i < applicationIdsStr.length; i++) {
            try {
                applicationIds[i] = Integer.parseInt(applicationIdsStr[i]);
            } catch (NumberFormatException e) {
                throw new ArgumentsParseException("application id must be integer");
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("timeBucket", timeBucket);
        JsonArray appInstances = new JsonArray();
        response.add("appInstances", appInstances);

        for (int applicationId : applicationIds) {
            appInstances.add(service.getInstances(timeBucket, applicationId));
        }
        return response;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
