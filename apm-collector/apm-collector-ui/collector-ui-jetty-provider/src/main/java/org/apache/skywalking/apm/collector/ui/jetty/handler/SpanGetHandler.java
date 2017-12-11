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
import org.apache.skywalking.apm.collector.ui.service.SpanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SpanGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(SpanGetHandler.class);

    @Override public String pathSpec() {
        return "/span/spanId";
    }

    private final SpanService service;

    public SpanGetHandler(ModuleManager moduleManager) {
        this.service = new SpanService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String segmentId = req.getParameter("segmentId");
        String spanIdStr = req.getParameter("spanId");
        logger.debug("segmentSpanId: {}, spanIdStr: {}", segmentId, spanIdStr);

        int spanId;
        try {
            spanId = Integer.parseInt(spanIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("span id must be integer");
        }

        return service.load(segmentId, spanId);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
