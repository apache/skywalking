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
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest.reader.TraceSegment;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest.reader.UpstreamSegmentJsonReader;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParseV2;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceSegmentCollectServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentCollectServletHandler.class);

    private final SegmentParseV2.Producer segmentProducer;

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

        try {
            BufferedReader bufferedReader = req.getReader();
            read(bufferedReader);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return null;
    }

    private UpstreamSegmentJsonReader jsonReader = new UpstreamSegmentJsonReader();

    private void read(BufferedReader bufferedReader) throws IOException {
        JsonReader reader = new JsonReader(bufferedReader);

        reader.beginArray();
        while (reader.hasNext()) {
            TraceSegment traceSegment = jsonReader.read(reader);
            segmentProducer.send(traceSegment.getUpstreamSegment(), SegmentSource.Agent);
        }
        reader.endArray();
    }
}
