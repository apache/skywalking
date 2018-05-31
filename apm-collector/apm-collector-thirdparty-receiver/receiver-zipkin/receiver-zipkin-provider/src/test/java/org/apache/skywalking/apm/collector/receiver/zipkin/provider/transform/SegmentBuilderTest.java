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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

/**
 * @author wusheng
 */
public class SegmentBuilderTest {
    @Test
    public void testTransform() throws UnsupportedEncodingException {
        List<Span> spanList = buildSpringSleuthExampleTrace();
        Assert.assertEquals(3, spanList.size());
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
}
