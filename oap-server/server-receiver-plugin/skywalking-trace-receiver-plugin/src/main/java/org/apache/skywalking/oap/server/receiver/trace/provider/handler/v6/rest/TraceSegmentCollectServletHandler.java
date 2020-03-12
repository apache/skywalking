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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest;

import com.google.gson.JsonElement;
import java.io.BufferedReader;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest.reader.UpstreamSegmentJsonReader;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParseV2;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceSegmentCollectServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentCollectServletHandler.class);

    private final SegmentParseV2.Producer segmentProducer;

    private UpstreamSegmentJsonReader upstreamSegmentJsonReader = new UpstreamSegmentJsonReader();

    public TraceSegmentCollectServletHandler(SegmentParseV2.Producer segmentProducer) {
        this.segmentProducer = segmentProducer;
    }

    @Override
    public String pathSpec() {
        return "/v2/segments";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) {
        if (logger.isDebugEnabled()) {
            logger.debug("receive stream segment");
        }

        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        try {
            BufferedReader reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            UpstreamSegment upstreamSegment = upstreamSegmentJsonReader.read(stringBuilder.toString()).build();

            segmentProducer.send(upstreamSegment, SegmentSource.Agent);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return null;
    }
}
