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
import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;

public class ReferenceJsonReader implements StreamJsonReader<SegmentReference> {

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();

    private static final String REF_TYPE_VALUE = "ref_type";
    private static final String PARENT_TRACE_SEGMENT_ID = "parent_trace_segment_id";
    private static final String PARENT_SPAN_ID = "parent_span_id";
    private static final String PARENT_SERVICE_INSTANCE_ID = "parent_service_instance_id";
    private static final String NETWORK_ADDRESS = "network_address";
    private static final String NETWORK_ADDRESS_ID = "network_address_id";
    private static final String ENTRY_SERVICE_INSTANCE_ID = "entry_service_instance_id";
    private static final String ENTRY_ENDPOINT = "entry_endpoint";
    private static final String ENTRY_ENDPOINT_ID = "entry_endpoint_id";
    private static final String PARENT_ENDPOINT = "parent_endpoint";
    private static final String PARENT_ENDPOINT_ID = "parent_endpoint_id";

    @Override
    public SegmentReference read(JsonReader reader) throws IOException {
        SegmentReference.Builder builder = SegmentReference.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case REF_TYPE_VALUE:
                    builder.setRefTypeValue(reader.nextInt());
                    break;
                case PARENT_TRACE_SEGMENT_ID:
                    builder.setParentTraceSegmentId(uniqueIdJsonReader.read(reader));
                    break;
                case PARENT_SPAN_ID:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case PARENT_SERVICE_INSTANCE_ID:
                    builder.setParentServiceInstanceId(reader.nextInt());
                    break;
                case NETWORK_ADDRESS:
                    builder.setNetworkAddress(reader.nextString());
                    break;
                case NETWORK_ADDRESS_ID:
                    builder.setNetworkAddressId(reader.nextInt());
                    break;
                case ENTRY_SERVICE_INSTANCE_ID:
                    builder.setEntryServiceInstanceId(reader.nextInt());
                    break;
                case ENTRY_ENDPOINT:
                    builder.setEntryEndpoint(reader.nextString());
                    break;
                case ENTRY_ENDPOINT_ID:
                    builder.setEntryEndpointId(reader.nextInt());
                    break;
                case PARENT_ENDPOINT:
                    builder.setParentEndpoint(reader.nextString());
                    break;
                case PARENT_ENDPOINT_ID:
                    builder.setParentEndpointId(reader.nextInt());
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
