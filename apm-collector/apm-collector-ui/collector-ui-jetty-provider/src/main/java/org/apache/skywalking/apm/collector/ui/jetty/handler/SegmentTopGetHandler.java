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
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.SegmentTopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentTopGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopGetHandler.class);

    @Override public String pathSpec() {
        return "/segment/top";
    }

    private final SegmentTopService service;

    public SegmentTopGetHandler(ModuleManager moduleManager) {
        this.service = new SegmentTopService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime") || !req.getParameterMap().containsKey("from") || !req.getParameterMap().containsKey("limit")) {
            throw new ArgumentsParseException("the request parameter must contains startTime, endTime, from, limit");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("startTime: {}, endTime: {}, from: {}", req.getParameter("startTime"), req.getParameter("endTime"), req.getParameter("from"));
        }

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

        int from;
        try {
            from = Integer.valueOf(req.getParameter("from"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter from must be an integer");
        }

        int limit;
        try {
            limit = Integer.valueOf(req.getParameter("limit"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter from must be an integer");
        }

        int minCost = -1;
        if (req.getParameterMap().containsKey("minCost")) {
            minCost = Integer.valueOf(req.getParameter("minCost"));
        }
        int maxCost = -1;
        if (req.getParameterMap().containsKey("maxCost")) {
            maxCost = Integer.valueOf(req.getParameter("maxCost"));
        }

        String globalTraceId = null;
        if (req.getParameterMap().containsKey("globalTraceId")) {
            globalTraceId = req.getParameter("globalTraceId");
        }

        String operationName = null;
        if (req.getParameterMap().containsKey("operationName")) {
            operationName = req.getParameter("operationName");
        }

        int applicationId;
        try {
            applicationId = Integer.valueOf(req.getParameter("applicationId"));
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("the request parameter applicationId must be a int");
        }

        ISegmentCostUIDAO.Error error;
        String errorStr = req.getParameter("error");
        if (StringUtils.isNotEmpty(errorStr)) {
            if ("true".equals(errorStr)) {
                error = ISegmentCostUIDAO.Error.True;
            } else if ("false".equals(errorStr)) {
                error = ISegmentCostUIDAO.Error.False;
            } else {
                error = ISegmentCostUIDAO.Error.All;
            }
        } else {
            error = ISegmentCostUIDAO.Error.All;
        }

        ISegmentCostUIDAO.Sort sort = ISegmentCostUIDAO.Sort.Cost;
        if (req.getParameterMap().containsKey("sort")) {
            String sortStr = req.getParameter("sort");
            if (sortStr.toLowerCase().equals(ISegmentCostUIDAO.Sort.Time.name().toLowerCase())) {
                sort = ISegmentCostUIDAO.Sort.Time;
            }
        }

        return service.loadTop(startTime, endTime, minCost, maxCost, operationName, globalTraceId, error, applicationId, limit, from, sort);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
