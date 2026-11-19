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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.Log;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.KeyNumericValue;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.Ref;
import org.apache.skywalking.oap.server.core.query.type.RefType;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.Trace;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.END_SECOND;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.MIDNIGHT;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.MIDNIGHT_SECOND;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.START_SECOND;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.event;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Two native traces and the events rover attaches to them. In the first, the consumer's exit span calls the provider,
 * whose entry span refers back to it, and the provider queries a database. Both sides' rover report the HTTP call
 * against the trace context it carried, the consumer's exit span: the consumer's request and response belong to that
 * span, the provider's to the provider's entry span.
 */
final class NativeTraceFixture {
    static final String TRACE = "3a5b7c9d0e1f.31.17368992000000001";
    static final String ONE_SEGMENT_TRACE = "3a5b7c9d0e1f.31.17368992000000002";
    /** Has only events, against the first trace's spans, and no query asks for it. */
    static final String UNQUERIED_TRACE = "3a5b7c9d0e1f.31.17368992000000003";
    /** Has only two events, on two spans of one segment, with the same name and start instant. */
    static final String TWO_SPANS_TRACE = "3a5b7c9d0e1f.31.17368992000000004";
    static final String UNKNOWN_TRACE = "3a5b7c9d0e1f.31.17368992000000009";
    private static final String CONSUMER = "3a5b7c9d0e1f.31.17368992000000011";
    private static final String PROVIDER = "4b6c8d0e1f2a.47.17368992000000012";
    private static final String WORKER = "5c7d9e1f2a3b.52.17368992000000013";
    private static final String TWO_SPANS_SEGMENT = "3a5b7c9d0e1f.31.17368992000000014";

    static final Map<Integer, String> COMPONENTS = Map.of(
        1, "Tomcat", 2, "JdkHttp", 33, "mysql-connector-java", 40, "kafka-producer", 41, "kafka-consumer");

    private static final SegmentObject CONSUMER_SEGMENT = SegmentObject.newBuilder()
        .setTraceId(TRACE).setTraceSegmentId(CONSUMER).setService("consumer").setServiceInstance("consumer-1")
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(0).setParentSpanId(-1)
                            .setStartTime(MIDNIGHT - 50_000L).setEndTime(MIDNIGHT + 50_000L)
                            .setOperationName("GET:/consumer").setSpanType(SpanType.Entry)
                            .setSpanLayer(SpanLayer.Http).setComponentId(1)
                            .addTags(tag("http.method", "GET")))
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(1).setParentSpanId(0)
                            .setStartTime(MIDNIGHT - 40_000L).setEndTime(MIDNIGHT + 40_000L)
                            .setOperationName("GET:/provider").setPeer("provider:8080").setSpanType(SpanType.Exit)
                            .setSpanLayer(SpanLayer.Http).setComponentId(2)
                            .addTags(tag("url", "http://provider:8080/provider")))
        .build();
    private static final SegmentObject PROVIDER_SEGMENT = SegmentObject.newBuilder()
        .setTraceId(TRACE).setTraceSegmentId(PROVIDER).setService("provider").setServiceInstance("provider-1")
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(0).setParentSpanId(-1)
                            .setStartTime(MIDNIGHT - 39_000L).setEndTime(MIDNIGHT + 39_000L)
                            .setOperationName("GET:/provider").setSpanType(SpanType.Entry)
                            .setSpanLayer(SpanLayer.Http).setComponentId(1)
                            .addRefs(SegmentReference.newBuilder()
                                                     .setRefType(
                                                         org.apache.skywalking.apm.network.language.agent.v3.RefType.CrossProcess)
                                                     .setTraceId(TRACE)
                                                     .setParentTraceSegmentId(CONSUMER)
                                                     .setParentSpanId(1)
                                                     .setParentService("consumer")
                                                     .setParentServiceInstance("consumer-1")
                                                     .setParentEndpoint("GET:/consumer")
                                                     .setNetworkAddressUsedAtPeer("provider:8080")))
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(1).setParentSpanId(0)
                            .setStartTime(MIDNIGHT - 30_000L).setEndTime(MIDNIGHT + 30_000L)
                            .setOperationName("Mysql/JDBC/PreparedStatement/execute").setPeer("mysql:3306")
                            .setSpanType(SpanType.Exit).setSpanLayer(SpanLayer.Database).setComponentId(33)
                            .setIsError(true)
                            .addTags(tag("db.statement", "select * from orders where id = ?"))
                            .addLogs(Log.newBuilder()
                                        .setTime(MIDNIGHT + 29_000L)
                                        .addData(tag("event", "error"))
                                        .addData(tag("message", "Lock wait timeout exceeded"))))
        .build();
    private static final SegmentObject WORKER_SEGMENT = SegmentObject.newBuilder()
        .setTraceId(ONE_SEGMENT_TRACE).setTraceSegmentId(WORKER).setService("worker").setServiceInstance("worker-1")
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(0).setParentSpanId(-1)
                            .setStartTime(MIDNIGHT - 20_000L).setEndTime(MIDNIGHT + 20_000L)
                            .setOperationName("Kafka/orders/Consumer/billing").setSpanType(SpanType.Entry)
                            .setSpanLayer(SpanLayer.MQ).setComponentId(41))
        .addSpans(SpanObject.newBuilder()
                            .setSpanId(1).setParentSpanId(0)
                            .setStartTime(MIDNIGHT - 10_000L).setEndTime(MIDNIGHT + 10_000L)
                            .setOperationName("Kafka/invoices/Producer").setPeer("kafka:9092")
                            .setSpanType(SpanType.Exit).setSpanLayer(SpanLayer.MQ).setComponentId(40))
        .build();
    static final List<SegmentObject> SEGMENTS = Arrays.asList(CONSUMER_SEGMENT, PROVIDER_SEGMENT, WORKER_SEGMENT);

    /** One millisecond before the start of the queries' duration. */
    static final SpanAttachedEvent CLIENT_REQUEST = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "1", START_SECOND - 1, 999_000_000,
        "HTTP Request Sampling", http("outbound", "request", "client")).build();
    /** On the start of the queries' duration. */
    static final SpanAttachedEvent CLIENT_WRITE = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "1", START_SECOND, 0,
        "Syscall write", "package_size", "182B", "package_count", "1")
        .addSummary(KeyIntValuePair.newBuilder().setKey("package_bytes").setValue(182L)).build();
    static final SpanAttachedEvent SERVER_REQUEST = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "1", MIDNIGHT_SECOND, 5,
        "HTTP Request Sampling", http("inbound", "request", "server")).build();
    static final SpanAttachedEvent DATABASE_SEND = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, PROVIDER, "1", MIDNIGHT_SECOND, 900_000_000,
        "Syscall sendto", "package_size", "96B", "package_count", "1").build();
    static final SpanAttachedEvent DATABASE_REQUEST = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, PROVIDER, "1", MIDNIGHT_SECOND, 950_000_000,
        "HTTP Request Sampling", http("outbound", "request", "client")).build();
    /** From the database's host, whose side has no span: it stays on the provider's exit span. */
    static final SpanAttachedEvent DATABASE_SERVER_REQUEST = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, PROVIDER, "1", MIDNIGHT_SECOND + 5, 0,
        "HTTP Request Sampling", http("inbound", "request", "server")).build();
    static final SpanAttachedEvent SERVER_RESPONSE = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "1", MIDNIGHT_SECOND + 10, 0,
        "HTTP Response Sampling", http("outbound", "response", "server")).build();
    static final SpanAttachedEvent UNKNOWN_SEGMENT_WRITE = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, "unknown-segment", "0", MIDNIGHT_SECOND + 20, 0,
        "Syscall write", "package_size", "64B", "package_count", "1").build();
    static final SpanAttachedEvent WORKER_SEND = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, ONE_SEGMENT_TRACE, WORKER, "1", MIDNIGHT_SECOND + 30, 0,
        "Syscall sendmsg", "package_size", "1KB", "package_count", "2").build();
    /** On the end of the queries' duration. */
    static final SpanAttachedEvent CLIENT_RESPONSE = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "1", END_SECOND, 0,
        "HTTP Response Sampling", http("inbound", "response", "client")).build();
    /** One millisecond after the end of the queries' duration, against a span id that is not a number. */
    static final SpanAttachedEvent NOT_A_SPAN_ID_READ = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, CONSUMER, "not-a-span-id", END_SECOND, 1_000_000,
        "Syscall read", "package_size", "182B", "package_count", "1").build();
    private static final SpanAttachedEvent UNQUERIED_TRACE_REQUEST = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, UNQUERIED_TRACE, CONSUMER, "1", MIDNIGHT_SECOND + 1, 0,
        "HTTP Request Sampling", http("outbound", "request", "client")).build();
    /** Stored with the Zipkin events, as its trace context's type says, though it names this trace. */
    private static final SpanAttachedEvent ZIPKIN_TYPED_WRITE = event(
        SpanAttachedEvent.SpanReferenceType.ZIPKIN, TRACE, "", "1", MIDNIGHT_SECOND + 2, 0,
        "Syscall write", "package_size", "182B", "package_count", "1").build();

    static final SpanAttachedEvent FIRST_SPAN_SEND = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TWO_SPANS_TRACE, TWO_SPANS_SEGMENT, "1",
        MIDNIGHT_SECOND + 40, 0, "Syscall sendto", "package_size", "96B", "package_count", "1").build();
    static final SpanAttachedEvent SECOND_SPAN_SEND = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TWO_SPANS_TRACE, TWO_SPANS_SEGMENT, "2",
        MIDNIGHT_SECOND + 40, 0, "Syscall sendto", "package_size", "96B", "package_count", "1").build();

    /** Neither in start order nor against it. */
    static final List<SpanAttachedEvent> EVENTS = Arrays.asList(
        DATABASE_SEND, CLIENT_RESPONSE, SECOND_SPAN_SEND, DATABASE_SERVER_REQUEST, UNQUERIED_TRACE_REQUEST,
        CLIENT_REQUEST, WORKER_SEND, ZIPKIN_TYPED_WRITE, SERVER_RESPONSE, FIRST_SPAN_SEND, DATABASE_REQUEST,
        NOT_A_SPAN_ID_READ, CLIENT_WRITE, UNKNOWN_SEGMENT_WRITE, SERVER_REQUEST);
    static final List<SpanAttachedEvent> EVENTS_OF_THE_TRACE = Arrays.asList(
        CLIENT_REQUEST, CLIENT_WRITE, SERVER_REQUEST, DATABASE_SEND, DATABASE_REQUEST, DATABASE_SERVER_REQUEST,
        SERVER_RESPONSE, UNKNOWN_SEGMENT_WRITE, CLIENT_RESPONSE, NOT_A_SPAN_ID_READ);
    static final List<SpanAttachedEvent> EVENTS_OF_BOTH_TRACES = Arrays.asList(
        CLIENT_REQUEST, CLIENT_WRITE, SERVER_REQUEST, DATABASE_SEND, DATABASE_REQUEST, DATABASE_SERVER_REQUEST,
        SERVER_RESPONSE, UNKNOWN_SEGMENT_WRITE, WORKER_SEND, CLIENT_RESPONSE, NOT_A_SPAN_ID_READ);

    private static final Gson GSON = new Gson();

    private NativeTraceFixture() {
    }

    /**
     * The trace's spans, each with the events that belong to it, keyed by their segment span id.
     */
    static Map<String, JsonElement> expectedSpans(final String traceId) {
        final List<Span> spans = TRACE.equals(traceId)
            ? Arrays.asList(
                expected(CONSUMER_SEGMENT, 0, CONSUMER + Const.SEGMENT_SPAN_SPLIT + -1, true),
                expected(CONSUMER_SEGMENT, 1, CONSUMER + Const.SEGMENT_SPAN_SPLIT + 0, false,
                         CLIENT_REQUEST, CLIENT_WRITE, CLIENT_RESPONSE),
                expected(PROVIDER_SEGMENT, 0, CONSUMER + Const.SEGMENT_SPAN_SPLIT + 1, false,
                         SERVER_REQUEST, SERVER_RESPONSE),
                expected(PROVIDER_SEGMENT, 1, PROVIDER + Const.SEGMENT_SPAN_SPLIT + 0, false,
                         DATABASE_SEND, DATABASE_REQUEST, DATABASE_SERVER_REQUEST))
            : Arrays.asList(
                expected(WORKER_SEGMENT, 0, WORKER + Const.SEGMENT_SPAN_SPLIT + -1, true),
                expected(WORKER_SEGMENT, 1, WORKER + Const.SEGMENT_SPAN_SPLIT + 0, false, WORKER_SEND));
        return render(spans);
    }

    /**
     * Every field of every span, keyed by the segment span id: the spans' order is not what is read here.
     */
    static Map<String, JsonElement> render(final Trace trace) {
        return render(trace.getSpans());
    }

    private static Map<String, JsonElement> render(final List<Span> spans) {
        final Map<String, JsonElement> rendered = new TreeMap<>();
        for (final Span span : spans) {
            assertNull(rendered.put(span.getSegmentSpanId(), GSON.toJsonTree(span)), span.getSegmentSpanId());
        }
        return rendered;
    }

    /**
     * The record {@code SegmentDispatcher} stores for a segment.
     */
    static SegmentRecord record(final SegmentObject segment) {
        final SpanObject first = segment.getSpans(0);
        final String serviceId = IDManager.ServiceID.buildId(segment.getService(), true);
        final SegmentRecord record = new SegmentRecord();
        record.setSegmentId(segment.getTraceSegmentId());
        record.setTraceId(segment.getTraceId());
        record.setServiceId(serviceId);
        record.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, segment.getServiceInstance()));
        record.setEndpointId(IDManager.EndpointID.buildId(serviceId, first.getOperationName()));
        record.setStartTime(first.getStartTime());
        record.setLatency((int) (first.getEndTime() - first.getStartTime()));
        record.setIsError(segment.getSpansList().stream().anyMatch(SpanObject::getIsError) ? 1 : 0);
        record.setDataBinary(segment.toByteArray());
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(first.getStartTime()));
        final List<String> tags = new ArrayList<>();
        for (final SpanObject span : segment.getSpansList()) {
            for (final KeyStringValuePair tag : span.getTagsList()) {
                tags.add(tag.getKey() + "=" + tag.getValue());
            }
        }
        record.setTags(tags);
        return record;
    }

    /**
     * A reported span as the trace query shows it, with the events attached to it in start order.
     */
    private static Span expected(final SegmentObject segment, final int index, final String segmentParentSpanId,
                                 final boolean root, final SpanAttachedEvent... events) {
        final SpanObject reported = segment.getSpans(index);
        final Span span = new Span();
        span.setTraceId(segment.getTraceId());
        span.setSegmentId(segment.getTraceSegmentId());
        span.setSpanId(reported.getSpanId());
        span.setParentSpanId(reported.getParentSpanId());
        for (final SegmentReference reference : reported.getRefsList()) {
            final Ref ref = new Ref();
            ref.setTraceId(reference.getTraceId());
            ref.setParentSegmentId(reference.getParentTraceSegmentId());
            ref.setParentSpanId(reference.getParentSpanId());
            ref.setType(RefType.CROSS_PROCESS);
            span.getRefs().add(ref);
        }
        span.setServiceCode(segment.getService());
        span.setServiceInstanceName(segment.getServiceInstance());
        span.setStartTime(reported.getStartTime());
        span.setEndTime(reported.getEndTime());
        span.setEndpointName(reported.getOperationName());
        span.setType(reported.getSpanType().name());
        span.setPeer(reported.getPeer());
        span.setComponent(COMPONENTS.get(reported.getComponentId()));
        span.setError(reported.getIsError());
        span.setLayer(reported.getSpanLayer().name());
        for (final KeyStringValuePair tag : reported.getTagsList()) {
            span.getTags().add(new KeyValue(tag.getKey(), tag.getValue()));
        }
        for (final Log log : reported.getLogsList()) {
            final LogEntity entity = new LogEntity();
            entity.setTime(log.getTime());
            for (final KeyStringValuePair data : log.getDataList()) {
                entity.getData().add(new KeyValue(data.getKey(), data.getValue()));
            }
            span.getLogs().add(entity);
        }
        span.setRoot(root);
        span.setSegmentSpanId(segment.getTraceSegmentId() + Const.SEGMENT_SPAN_SPLIT + reported.getSpanId());
        span.setSegmentParentSpanId(segmentParentSpanId);
        for (final SpanAttachedEvent event : events) {
            final org.apache.skywalking.oap.server.core.query.type.SpanAttachedEvent attached =
                new org.apache.skywalking.oap.server.core.query.type.SpanAttachedEvent();
            attached.getStartTime().setSeconds(event.getStartTime().getSeconds());
            attached.getStartTime().setNanos(event.getStartTime().getNanos());
            attached.getEndTime().setSeconds(event.getEndTime().getSeconds());
            attached.getEndTime().setNanos(event.getEndTime().getNanos());
            attached.setEvent(event.getEvent());
            for (final KeyStringValuePair tag : event.getTagsList()) {
                attached.getTags().add(new KeyValue(tag.getKey(), tag.getValue()));
            }
            for (final KeyIntValuePair summary : event.getSummaryList()) {
                attached.getSummary().add(new KeyNumericValue(summary.getKey(), summary.getValue()));
            }
            span.getAttachedEvents().add(attached);
        }
        return span;
    }

    /** The tags rover's HTTP sampling puts on an event. */
    static String[] http(final String direction, final String type, final String role) {
        return new String[] {
            "data_size", "182B",
            "data_direction", direction,
            "data_type", type,
            "connection_role", role
        };
    }

    private static KeyStringValuePair tag(final String key, final String value) {
        return KeyStringValuePair.newBuilder().setKey(key).setValue(value).build();
    }
}
