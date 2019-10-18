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
import org.apache.skywalking.apm.network.language.agent.TraceSegmentReference;

/**
 * @author peng-yongsheng
 */
public class ReferenceJsonReader implements StreamJsonReader<TraceSegmentReference> {

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();

    private static final String PARENT_TRACE_SEGMENT_ID = "pts";
    private static final String PARENT_APPLICATION_INSTANCE_ID = "pii";
    private static final String PARENT_SPAN_ID = "psp";
    private static final String PARENT_SERVICE_ID = "psi";
    private static final String PARENT_SERVICE_NAME = "psn";
    private static final String NETWORK_ADDRESS_ID = "ni";
    private static final String NETWORK_ADDRESS = "nn";
    private static final String ENTRY_APPLICATION_INSTANCE_ID = "eii";
    private static final String ENTRY_SERVICE_ID = "esi";
    private static final String ENTRY_SERVICE_NAME = "esn";
    private static final String REF_TYPE_VALUE = "rv";

    @Override public TraceSegmentReference read(JsonReader reader) throws IOException {
        TraceSegmentReference.Builder builder = TraceSegmentReference.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case PARENT_TRACE_SEGMENT_ID:
                    builder.setParentTraceSegmentId(uniqueIdJsonReader.read(reader));
                    break;
                case PARENT_APPLICATION_INSTANCE_ID:
                    builder.setParentApplicationInstanceId(reader.nextInt());
                    break;
                case PARENT_SPAN_ID:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case PARENT_SERVICE_ID:
                    builder.setParentServiceId(reader.nextInt());
                    break;
                case PARENT_SERVICE_NAME:
                    builder.setParentServiceName(reader.nextString());
                    break;
                case NETWORK_ADDRESS_ID:
                    builder.setNetworkAddressId(reader.nextInt());
                    break;
                case NETWORK_ADDRESS:
                    builder.setNetworkAddress(reader.nextString());
                    break;
                case ENTRY_APPLICATION_INSTANCE_ID:
                    builder.setEntryApplicationInstanceId(reader.nextInt());
                    break;
                case ENTRY_SERVICE_ID:
                    builder.setEntryServiceId(reader.nextInt());
                    break;
                case ENTRY_SERVICE_NAME:
                    builder.setEntryServiceName(reader.nextString());
                    break;
                case REF_TYPE_VALUE:
                    builder.setRefTypeValue(reader.nextInt());
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
