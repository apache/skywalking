/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.SpanService;
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

    private SpanService service = new SpanService();

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
