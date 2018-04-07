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

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.agent.jetty.provider.handler.reader.TraceSegment;
import org.apache.skywalking.apm.collector.agent.jetty.provider.handler.reader.TraceSegmentJsonReader;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentServletHandler extends JettyHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentServletHandler.class);

    private final ISegmentParseService segmentParseService;

    public TraceSegmentServletHandler(ModuleManager moduleManager) {
        this.segmentParseService = moduleManager.find(AnalysisSegmentParserModule.NAME).getService(ISegmentParseService.class);
    }

    @Override public String pathSpec() {
        return "/segments";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        logger.debug("receive stream segment");
        try {
            BufferedReader bufferedReader = req.getReader();
            read(bufferedReader);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    private TraceSegmentJsonReader jsonReader = new TraceSegmentJsonReader();

    private void read(BufferedReader bufferedReader) throws IOException {
        JsonReader reader = new JsonReader(bufferedReader);

        reader.beginArray();
        while (reader.hasNext()) {
            TraceSegment traceSegment = jsonReader.read(reader);
            segmentParseService.parse(traceSegment.getUpstreamSegment(), ISegmentParseService.Source.Agent);
        }
        reader.endArray();
    }
}
