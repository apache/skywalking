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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SegmentJsonReader implements StreamJsonReader<SegmentObject.Builder> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentJsonReader.class);

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();
    private SpanJsonReader spanJsonReader = new SpanJsonReader();

    private static final String TRACE_SEGMENT_ID = "trace_segment_id";
    private static final String SPANS = "spans";
    private static final String SERVICE_ID = "service_id";
    private static final String SERVICE_INSTANCE_ID = "service_instance_id";
    private static final String IS_SIZE_LIMITED = "is_size_limited";

    @Override
    public SegmentObject.Builder read(JsonReader reader) throws IOException {
        SegmentObject.Builder builder = SegmentObject.newBuilder();

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
                case SERVICE_ID:
                    builder.setServiceId(reader.nextInt());
                    break;
                case SERVICE_INSTANCE_ID:
                    builder.setServiceInstanceId(reader.nextInt());
                    break;
                case IS_SIZE_LIMITED:
                    builder.setIsSizeLimited(reader.nextBoolean());
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
