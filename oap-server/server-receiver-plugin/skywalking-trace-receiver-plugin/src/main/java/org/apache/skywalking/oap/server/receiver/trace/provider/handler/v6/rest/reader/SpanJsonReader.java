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
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;

public class SpanJsonReader implements StreamJsonReader<SpanObjectV2> {

    private KeyStringValuePairJsonReader keyStringValuePairJsonReader = new KeyStringValuePairJsonReader();
    private LogJsonReader logJsonReader = new LogJsonReader();
    private ReferenceJsonReader referenceJsonReader = new ReferenceJsonReader();

    private static final String SPAN_ID = "span_id";
    private static final String PARENT_SPAN_ID = "parent_span_id";
    private static final String START_TIME = "start_time";
    private static final String END_TIME = "end_time";
    private static final String REFS = "refs";
    private static final String OPERATION_NAME_ID = "operation_name_id";
    private static final String OPERATION_NAME = "operation_name";
    private static final String PEER_ID = "peer_id";
    private static final String PEER = "peer";
    private static final String SPAN_TYPE = "span_type";
    private static final String SPAN_LAYER = "span_layer";
    private static final String COMPONENT_ID = "component_id";
    private static final String COMPONENT = "component";
    private static final String IS_ERROR = "is_error";
    private static final String TAGS = "tags";
    private static final String LOGS = "logs";

    @Override
    public SpanObjectV2 read(JsonReader reader) throws IOException {
        SpanObjectV2.Builder builder = SpanObjectV2.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case SPAN_ID:
                    builder.setSpanId(reader.nextInt());
                    break;
                case SPAN_TYPE:
                    builder.setSpanTypeValue(reader.nextInt());
                    break;
                case SPAN_LAYER:
                    builder.setSpanLayerValue(reader.nextInt());
                    break;
                case PARENT_SPAN_ID:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case START_TIME:
                    builder.setStartTime(reader.nextLong());
                    break;
                case END_TIME:
                    builder.setEndTime(reader.nextLong());
                    break;
                case COMPONENT_ID:
                    builder.setComponentId(reader.nextInt());
                    break;
                case COMPONENT:
                    builder.setComponent(reader.nextString());
                    break;
                case OPERATION_NAME_ID:
                    builder.setOperationNameId(reader.nextInt());
                    break;
                case OPERATION_NAME:
                    builder.setOperationName(reader.nextString());
                    break;
                case PEER_ID:
                    builder.setPeerId(reader.nextInt());
                    break;
                case PEER:
                    builder.setPeer(reader.nextString());
                    break;
                case IS_ERROR:
                    builder.setIsError(reader.nextBoolean());
                    break;
                case REFS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addRefs(referenceJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                case TAGS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addTags(keyStringValuePairJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                case LOGS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addLogs(logJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return builder.build();
    }
}
