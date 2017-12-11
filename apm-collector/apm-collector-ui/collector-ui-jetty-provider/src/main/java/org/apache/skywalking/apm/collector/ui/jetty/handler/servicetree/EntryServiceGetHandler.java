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


package org.apache.skywalking.apm.collector.ui.jetty.handler.servicetree;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.ServiceTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class EntryServiceGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(EntryServiceGetHandler.class);

    @Override public String pathSpec() {
        return "/service/entry";
    }

    private final ServiceTreeService service;

    public EntryServiceGetHandler(ModuleManager moduleManager) {
        this.service = new ServiceTreeService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("applicationId") || !req.getParameterMap().containsKey("entryServiceName")
            || !req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime")
            || !req.getParameterMap().containsKey("from") || !req.getParameterMap().containsKey("size")) {
            throw new ArgumentsParseException("must contains parameters: applicationId, entryServiceName, startTime, endTime, from, size");
        }

        String applicationIdStr = req.getParameter("applicationId");
        String entryServiceName = req.getParameter("entryServiceName");
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        String fromStr = req.getParameter("from");
        String sizeStr = req.getParameter("size");
        logger.debug("service entry get applicationId: {}, entryServiceName: {}, startTime: {}, endTime: {}, from: {}, size: {}", applicationIdStr, entryServiceName, startTimeStr, endTimeStr, fromStr, sizeStr);

        int applicationId;
        try {
            applicationId = Integer.parseInt(applicationIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("application id must be integer");
        }

        long startTime;
        try {
            startTime = Long.parseLong(startTimeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("start time must be long");
        }

        long endTime;
        try {
            endTime = Long.parseLong(endTimeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("end time must be long");
        }

        int from;
        try {
            from = Integer.parseInt(fromStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("from must be integer");
        }

        int size;
        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("size must be integer");
        }

        return service.loadEntryService(applicationId, entryServiceName, startTime, endTime, from, size);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
