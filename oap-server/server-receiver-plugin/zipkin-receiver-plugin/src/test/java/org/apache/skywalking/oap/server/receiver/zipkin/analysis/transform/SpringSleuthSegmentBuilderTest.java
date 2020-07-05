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

package org.apache.skywalking.oap.server.receiver.zipkin.analysis.transform;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.data.SkyWalkingTrace;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.data.ZipkinTrace;
import org.junit.Assert;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

public class SpringSleuthSegmentBuilderTest implements SegmentListener {
    @Test
    public void testTransform() throws Exception {

        Zipkin2SkyWalkingTransfer.INSTANCE.addListener(this);

        List<Span> spanList = buildSpringSleuthExampleTrace();
        Assert.assertEquals(3, spanList.size());

        ZipkinTrace trace = new ZipkinTrace();
        spanList.forEach(span -> trace.addSpan(span));

        Zipkin2SkyWalkingTransfer.INSTANCE.transfer(trace);
    }

    private List<Span> buildSpringSleuthExampleTrace() throws UnsupportedEncodingException {
        List<Span> spans = new LinkedList<>();
        String span = "{\"traceId\":\"5b0e64354eea4fa71a8a1b5bdd791b8a\",\"id\":\"1a8a1b5bdd791b8a\",\"kind\":\"SERVER\",\"name\":\"get /\",\"timestamp\":1527669813700123,\"duration\":11295,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"192.168.72.220\"},\"remoteEndpoint\":{\"ipv6\":\"::1\",\"port\":55146},\"tags\":{\"http.method\":\"GET\",\"http.path\":\"/\",\"mvc.controller.class\":\"Frontend\",\"mvc.controller.method\":\"callBackend\"}}";
        spans.add(SpanBytesDecoder.JSON_V2.decodeOne(span.getBytes("UTF-8")));
        span = "{\"traceId\":\"5b0e64354eea4fa71a8a1b5bdd791b8a\",\"parentId\":\"1a8a1b5bdd791b8a\",\"id\":\"d7d5b93dcda767c8\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1527669813702456,\"duration\":6672,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"192.168.72.220\"},\"tags\":{\"http.method\":\"GET\",\"http.path\":\"/api\"}}";
        spans.add(SpanBytesDecoder.JSON_V2.decodeOne(span.getBytes("UTF-8")));
        span = "{\"traceId\":\"5b0e64354eea4fa71a8a1b5bdd791b8a\",\"parentId\":\"1a8a1b5bdd791b8a\",\"id\":\"d7d5b93dcda767c8\",\"kind\":\"SERVER\",\"name\":\"get /api\",\"timestamp\":1527669813705106,\"duration\":4802,\"localEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.72.220\"},\"remoteEndpoint\":{\"ipv4\":\"127.0.0.1\",\"port\":55147},\"tags\":{\"http.method\":\"GET\",\"http.path\":\"/api\",\"mvc.controller.class\":\"Backend\",\"mvc.controller.method\":\"printDate\"},\"shared\":true}";
        spans.add(SpanBytesDecoder.JSON_V2.decodeOne(span.getBytes("UTF-8")));

        return SpanBytesDecoder.JSON_V2.decodeList(spans.toString().getBytes("UTF-8"));
    }

    @Override
    public void notify(SkyWalkingTrace trace) {
        List<SegmentObject.Builder> segments = trace.getSegmentList();
        Assert.assertEquals(2, segments.size());
        SegmentObject.Builder builder = segments.get(0);
        SegmentObject.Builder builder1 = segments.get(1);
        SegmentObject.Builder front, end;
        if (builder.getService().equals("frontend")) {
            front = builder;
            end = builder1;
        } else if (builder.getService().equals("backend")) {
            end = builder;
            front = builder1;
        } else {
            Assert.fail("Can't find frontend and backend applications, " + builder.getService());
            return;
        }

        Assert.assertEquals(2, front.getSpansCount());
        Assert.assertEquals(1, end.getSpansCount());

        front.getSpansList().forEach(spanObject -> {
            if (spanObject.getSpanId() == 0) {
                // span id = 1, means incoming http of frontend
                Assert.assertEquals(SpanType.Entry, spanObject.getSpanType());
                Assert.assertEquals("get /", spanObject.getOperationName());
                Assert.assertEquals(0, spanObject.getSpanId());
                Assert.assertEquals(-1, spanObject.getParentSpanId());
            } else if (spanObject.getSpanId() == 1) {
                Assert.assertEquals("192.168.72.220", spanObject.getPeer());
                Assert.assertEquals(SpanType.Exit, spanObject.getSpanType());
                Assert.assertEquals(1, spanObject.getSpanId());
                Assert.assertEquals(0, spanObject.getParentSpanId());
            } else {
                Assert.fail("Only two spans expected");
            }
            Assert.assertTrue(spanObject.getTagsCount() > 0);
        });

        SpanObject spanObject = end.getSpans(0);

        Assert.assertEquals(1, spanObject.getRefsCount());
        SegmentReference spanObjectRef = spanObject.getRefs(0);
        Assert.assertEquals("get /", spanObjectRef.getParentEndpoint());
        Assert.assertEquals("192.168.72.220", spanObjectRef.getNetworkAddressUsedAtPeer());
        Assert.assertEquals(1, spanObjectRef.getParentSpanId());
        Assert.assertEquals(front.getTraceSegmentId(), spanObjectRef.getParentTraceSegmentId());

        Assert.assertTrue(spanObject.getTagsCount() > 0);

        Assert.assertEquals("get /api", spanObject.getOperationName());
        Assert.assertEquals(0, spanObject.getSpanId());
        Assert.assertEquals(-1, spanObject.getParentSpanId());
        Assert.assertEquals(SpanType.Entry, spanObject.getSpanType());
    }
}
