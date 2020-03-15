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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.segment;

import java.io.IOException;
import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.junit.Assert;
import org.junit.Test;

public class ProtoBufJsonUtilsTest {
    @Test
    public void testProtoBuf() {
        String json = "{\n" +
            "  \"spans\": [\n" +
            "    {\n" +
            "      \"operationName\": \"/tier2/lb\",\n" +
            "      \"startTime\": 1582526028207,\n" +
            "      \"endTime\": 1582526028221,\n" +
            "      \"spanType\": \"Exit\",\n" +
            "      \"spanId\": 1,\n" +
            "      \"isError\": false,\n" +
            "      \"parentSpanId\": 0,\n" +
            "      \"componentId\": 6000,\n" +
            "      \"peer\": \"User Service Name-nginx:upstream_ip:port\",\n" +
            "      \"spanLayer\": \"Http\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"operationName\": \"/tier2/lb\",\n" +
            "      \"startTime\": 1582526028207,\n" +
            "      \"tags\": [\n" +
            "        {\n" +
            "          \"key\": \"http.method\",\n" +
            "          \"value\": \"GET\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"http.params\",\n" +
            "          \"value\": \"http://127.0.0.1/tier2/lb\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"endTime\": 1582526028221,\n" +
            "      \"spanType\": \"Entry\",\n" +
            "      \"spanId\": 0,\n" +
            "      \"isError\": false,\n" +
            "      \"parentSpanId\": -1,\n" +
            "      \"componentId\": 6000,\n" +
            "      \"refs\": [\n" +
            "        {\n" +
            "          \"parentTraceSegmentId\": {\n" +
            "            \"idParts\": [\n" +
            "              1582526028032,\n" +
            "              794206293,\n" +
            "              69887\n" +
            "            ]\n" +
            "          },\n" +
            "          \"parentEndpointId\": 0,\n" +
            "          \"entryEndpointId\": 0,\n" +
            "          \"parentServiceInstanceId\": 1,\n" +
            "          \"parentEndpoint\": \"/ingress\",\n" +
            "          \"networkAddress\": \"#User Service Name-nginx:upstream_ip:port\",\n" +
            "          \"parentSpanId\": 1,\n" +
            "          \"entryServiceInstanceId\": 1,\n" +
            "          \"networkAddressId\": 0,\n" +
            "          \"entryEndpoint\": \"/ingress\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"spanLayer\": \"Http\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"serviceInstanceId\": 1,\n" +
            "  \"serviceId\": 1,\n" +
            "  \"traceSegmentId\": {\n" +
            "    \"idParts\": [\n" +
            "      1582526028040,\n" +
            "      794206293,\n" +
            "      69887\n" +
            "    ]\n" +
            "  },\n" +
            "  \"globalTraceIds\": [\n" +
            "    {\n" +
            "      \"idParts\": [\n" +
            "        1582526028032,\n" +
            "        794206293,\n" +
            "        69887\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        UpstreamSegment.Builder builder = UpstreamSegment.newBuilder();
        try {
            ProtoBufJsonUtils.fromJSON(json, builder);
            UpstreamSegment upstreamSegment = builder.build();
            Assert.assertEquals(1582526028032L, upstreamSegment.getGlobalTraceIds(0).getIdParts(0));

            SegmentObject.Builder segBuilder = SegmentObject.newBuilder();
            ProtoBufJsonUtils.fromJSON(json, segBuilder);
            SegmentObject segmentObject = segBuilder.build();
            Assert.assertEquals(2, segmentObject.getSpansCount());
            Assert.assertEquals(SpanLayer.Http, segmentObject.getSpans(0).getSpanLayer());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testToJson() {
        String json = "{\n" +
            "  \"commands\": [{\n" +
            "  }]\n" +
            "}";
        try {
            Command command = Command.newBuilder().build();
            final Commands nextCommands = Commands.newBuilder().addCommands(command).build();
            Assert.assertEquals(json, ProtoBufJsonUtils.toJSON(nextCommands));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
