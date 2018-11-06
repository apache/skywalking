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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v5.rest.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.apache.skywalking.apm.network.language.agent.TraceSegmentObject;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentJsonReader implements StreamJsonReader<TraceSegmentObject.Builder> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentJsonReader.class);

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();
    private SpanJsonReader spanJsonReader = new SpanJsonReader();

    private static final String TRACE_SEGMENT_ID = "ts";
    private static final String APPLICATION_ID = "ai";
    private static final String APPLICATION_INSTANCE_ID = "ii";
    private static final String SPANS = "ss";

    @Override public TraceSegmentObject.Builder read(JsonReader reader) throws IOException {
        TraceSegmentObject.Builder builder = TraceSegmentObject.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case TRACE_SEGMENT_ID:
                    builder.setTraceSegmentId(uniqueIdJsonReader.read(reader));
                    if (logger.isDebugEnabled()) {
                        StringBuilder segmentId = new StringBuilder();
                        builder.getTraceSegmentId().getIdPartsList().forEach(idPart -> segmentId.append(idPart));
                        logger.debug("segment id: {}", segmentId);
                    }
                    break;
                case APPLICATION_ID:
                    builder.setApplicationId(reader.nextInt());
                    break;
                case APPLICATION_INSTANCE_ID:
                    builder.setApplicationInstanceId(reader.nextInt());
                    break;
                case SPANS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addSpans(spanJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return builder;
    }
}
