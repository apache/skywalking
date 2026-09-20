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
 */

package org.apache.skywalking.oap.query.traceql.converter;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OTLPSpanMatcherTest {
    private static final Resource RESOURCE = Resource.newBuilder()
                                                     .addAttributes(string("k8s.deployment.name", "checkout-deploy"))
                                                     .addAttributes(string("service.instance", "checkout-1"))
                                                     .build();
    private static final InstrumentationScope SCOPE = InstrumentationScope.newBuilder().setName("io.opentelemetry.grpc").build();
    private static final Span SPAN = Span.newBuilder()
                                         .setName("Charge")
                                         .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
                                         .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR))
                                         .setStartTimeUnixNano(1_000_000L)
                                         .setEndTimeUnixNano(3_000_000L)
                                         .addAttributes(string("peer.service", "payment"))
                                         .addAttributes(KeyValue.newBuilder()
                                                                .setKey("http.response.status_code")
                                                                .setValue(AnyValue.newBuilder().setIntValue(503)))
                                         .build();

    @Test
    void shouldMatchAnEmptyCondition() {
        assertTrue(new OTLPSpanMatcher(new OTLPTraceQueryCondition()).matches(RESOURCE, SCOPE, SPAN));
    }

    @Test
    void shouldResolveServiceAndInstanceLikeTheReceiver() {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setServiceName("checkout-deploy");
        condition.setServiceInstance("checkout-1");
        assertTrue(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));

        condition.setServiceName("frontend");
        assertFalse(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));
    }

    @Test
    void shouldMatchColumnsAndRanges() {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setScopeName("io.opentelemetry.grpc");
        condition.setSpanName("Charge");
        condition.setPeerService("payment");
        condition.setKind(Span.SpanKind.SPAN_KIND_CLIENT.getNumber());
        condition.setStatusCode(Status.StatusCode.STATUS_CODE_ERROR.getNumber());
        condition.setMinDurationNanos(2_000_000L);
        condition.setMaxDurationNanos(2_000_000L);
        assertTrue(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));

        condition.setMinDurationNanos(2_000_001L);
        assertFalse(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));
        condition.setMinDurationNanos(0L);
        condition.setKind(Span.SpanKind.SPAN_KIND_SERVER.getNumber());
        assertFalse(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));
    }

    @Test
    void shouldMatchTagsAsTheIndexRendersThem() {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setTags(Collections.singletonList(new Tag("http.response.status_code", "503")));
        assertTrue(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));

        condition.setTags(Collections.singletonList(new Tag("k8s.deployment.name", "checkout-deploy")));
        assertTrue(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));

        condition.setTags(Collections.singletonList(new Tag("http.response.status_code", "200")));
        assertFalse(new OTLPSpanMatcher(condition).matches(RESOURCE, SCOPE, SPAN));
    }

    private static KeyValue string(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }
}
