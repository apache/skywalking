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
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.junit.Assert;
import org.junit.Test;

public class ProtoBufJsonUtilsTest {
    @Test
    public void testProtoBuf() throws IOException {
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
            "          \"parentTraceId\": \"abc.mocktraceid\",\n" +
            "          \"parentTraceSegmentId\": \"abc.mocksegmentid\",\n" +
            "          \"parentEndpointName\": \"/access/uri\",\n" +
            "          \"parentService\": \"service\",\n" +
            "          \"parentServiceInstance\": \"instance\",\n" +
            "          \"networkAddress\": \"#User Service Name-nginx:upstream_ip:port\",\n" +
            "          \"parentSpanId\": 1,\n" +
            "          \"networkAddressUsedAtPeer\": \"127.0.0.1\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"spanLayer\": \"Http\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"serviceInstance\": \"instance\",\n" +
            "  \"service\": \"service\",\n" +
            "  \"traceSegmentId\": \"mocksegmentid\",\n" +
            "  \"traceId\": \"mocktraceid\"\n" +
            "}";

        SegmentObject.Builder segBuilder = SegmentObject.newBuilder();
        ProtoBufJsonUtils.fromJSON(json, segBuilder);
        SegmentObject segmentObject = segBuilder.build();
        Assert.assertEquals("mocktraceid", segmentObject.getTraceId());
        Assert.assertEquals(2, segmentObject.getSpansCount());
        Assert.assertEquals(SpanLayer.Http, segmentObject.getSpans(0).getSpanLayer());

    }

    @Test
    public void testToJson() throws IOException {
        String json = "{\n" +
            "  \"commands\": [{\n" +
            "  }]\n" +
            "}";
        Command command = Command.newBuilder().build();
        final Commands nextCommands = Commands.newBuilder().addCommands(command).build();
        Assert.assertEquals(json, ProtoBufJsonUtils.toJSON(nextCommands));
    }
}
