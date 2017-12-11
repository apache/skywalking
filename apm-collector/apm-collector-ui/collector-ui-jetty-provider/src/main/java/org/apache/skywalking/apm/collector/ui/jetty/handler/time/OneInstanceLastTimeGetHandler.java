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


package org.apache.skywalking.apm.collector.ui.jetty.handler.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.TimeSynchronousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class OneInstanceLastTimeGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(OneInstanceLastTimeGetHandler.class);

    @Override public String pathSpec() {
        return "/time/oneInstance";
    }

    private final TimeSynchronousService service;

    public OneInstanceLastTimeGetHandler(ModuleManager moduleManager) {
        this.service = new TimeSynchronousService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String instanceIdStr = req.getParameter("instanceId");
        logger.debug("instanceId: {}", instanceIdStr);

        int instanceId;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("application instance id must be integer");
        }

        Long time = service.instanceLastTime(instanceId);
        logger.debug("application instance id: {}, instance last time: {}", instanceId, time);
        JsonObject timeJson = new JsonObject();
        timeJson.addProperty("timeBucket", time);
        return timeJson;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
