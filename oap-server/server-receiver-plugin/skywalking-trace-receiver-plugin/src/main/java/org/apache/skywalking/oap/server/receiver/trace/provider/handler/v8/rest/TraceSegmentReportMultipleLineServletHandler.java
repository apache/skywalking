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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.rest;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParserListenerManager;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsing segments from multiple lines
 */
public class TraceSegmentReportMultipleLineServletHandler extends TraceSegmentReportBaseServletHandler {

    public TraceSegmentReportMultipleLineServletHandler(ModuleManager moduleManager, SegmentParserListenerManager listenerManager, TraceServiceModuleConfig config) {
        super(moduleManager, listenerManager, config);
    }

    @Override
    protected List<SegmentObject> parseSegments(HttpServletRequest req) throws IOException {
        final List<SegmentObject> segments = new ArrayList<>();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            SegmentObject.Builder upstreamSegmentBuilder = SegmentObject.newBuilder();
            ProtoBufJsonUtils.fromJSON(line, upstreamSegmentBuilder);
            segments.add(upstreamSegmentBuilder.build());
        }

        return segments;
    }

    @Override
    public String pathSpec() {
        return "/v3/segments";
    }
}
