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


package org.apache.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.TraceDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceDagGetHandler.class);

    @Override public String pathSpec() {
        return "/traceDag";
    }

    private final TraceDagService service;

    public TraceDagGetHandler(ModuleManager moduleManager) {
        this.service = new TraceDagService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime")) {
            throw new ArgumentsParseException("the request parameter must contains startTime, endTime");
        }

        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        logger.debug("startTime: {}, endTimeStr: {}", startTimeStr, endTimeStr);

        long startTime;
        try {
            startTime = Long.valueOf(req.getParameter("startTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter startTime must be a long");
        }

        long endTime;
        try {
            endTime = Long.valueOf(req.getParameter("endTime"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter endTime must be a long");
        }

        return service.load(startTime, endTime);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
