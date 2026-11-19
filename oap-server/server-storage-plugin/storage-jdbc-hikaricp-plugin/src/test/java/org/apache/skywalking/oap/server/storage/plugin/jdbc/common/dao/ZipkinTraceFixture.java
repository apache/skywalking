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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.yaml.snakeyaml.Yaml;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.MIDNIGHT;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.MIDNIGHT_SECOND;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.AttachedEventFixture.event;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.NativeTraceFixture.http;

/**
 * Two Zipkin traces and the events rover attaches to them. In the first, the frontend's client span calls the
 * backend, whose server span is its child (B3 without shared span ids), and the backend queries a database. Both
 * sides' rover report the HTTP call against the B3 context it carried, the client span: the frontend's request and
 * response belong to that span, the backend's to the backend's server span. Rover reports a Zipkin context without a
 * segment id. The backend's server span also calls the inventory with its own span id, as a service traced on its
 * server side only does, so it carries the client side of that call besides the server side of the frontend's.
 */
final class ZipkinTraceFixture {
    static final String TRACE = "463ac35c9f6413ad48485a3953bb6124";
    static final String ONE_SERVICE_TRACE = "463ac35c9f6413ad48485a3953bb6125";
    /** Has only events, against the first trace's spans, and no query asks for it. */
    static final String UNQUERIED_TRACE = "463ac35c9f6413ad48485a3953bb6126";
    /** Each has only an event, on the same span id as the other's, with the same name and start instant. */
    static final String SAME_SPAN_ID_TRACE = "463ac35c9f6413ad48485a3953bb6127";
    static final String OTHER_SAME_SPAN_ID_TRACE = "463ac35c9f6413ad48485a3953bb6128";
    /** Has only two events, on two spans, with the same name and start instant. */
    static final String TWO_SPANS_TRACE = "463ac35c9f6413ad48485a3953bb6129";
    static final String UNKNOWN_TRACE = "463ac35c9f6413ad48485a3953bb6fff";
    private static final String ROOT = "1000000000000001";
    private static final String CLIENT = "1000000000000002";
    private static final String SERVER = "1000000000000003";
    private static final String DATABASE = "1000000000000004";
    private static final String INVENTORY = "1000000000000005";
    private static final String JOB = "2000000000000001";
    private static final String LOAD = "2000000000000002";

    private static final Span ROOT_SPAN = span(TRACE, ROOT, null, Span.Kind.SERVER, "get /", "frontend",
                                               MIDNIGHT - 35_000L, 30_000_000L).build();
    private static final Span CLIENT_SPAN = span(TRACE, CLIENT, ROOT, Span.Kind.CLIENT, "get /api", "frontend",
                                                 MIDNIGHT - 31_000L, 20_000_000L)
        .remoteEndpoint(Endpoint.newBuilder().serviceName("backend").ip("10.0.0.2").port(8080).build()).build();
    private static final Span SERVER_SPAN = span(TRACE, SERVER, CLIENT, Span.Kind.SERVER, "get /api", "backend",
                                                 MIDNIGHT - 30_500L, 19_000_000L).build();
    private static final Span DATABASE_SPAN = span(TRACE, DATABASE, SERVER, Span.Kind.CLIENT, "select orders",
                                                   "backend", MIDNIGHT - 26_000L, 1_000_000L)
        .remoteEndpoint(Endpoint.newBuilder().serviceName("mysql").build())
        .putTag("sql.query", "select * from orders").build();
    /** Called by the backend's server span with that span's id, without a client span between them. */
    private static final Span INVENTORY_SPAN = span(TRACE, INVENTORY, SERVER, Span.Kind.SERVER, "get /stock",
                                                    "inventory", MIDNIGHT - 22_500L, 2_000_000L).build();
    private static final Span JOB_SPAN = span(ONE_SERVICE_TRACE, JOB, null, Span.Kind.SERVER, "run job", "batch",
                                              MIDNIGHT - 20_000L, 5_000_000L).build();
    private static final Span LOAD_SPAN = span(ONE_SERVICE_TRACE, LOAD, JOB, Span.Kind.CLIENT, "load batch", "batch",
                                               MIDNIGHT - 19_000L, 1_000_000L)
        .remoteEndpoint(Endpoint.newBuilder().serviceName("postgres").build()).build();
    static final List<Span> SPANS = Arrays.asList(
        ROOT_SPAN, CLIENT_SPAN, SERVER_SPAN, DATABASE_SPAN, INVENTORY_SPAN, JOB_SPAN, LOAD_SPAN);

    static final SpanAttachedEvent CLIENT_REQUEST = zipkinEvent(
        TRACE, CLIENT, MIDNIGHT_SECOND - 30, 100, "HTTP Request Sampling", http("outbound", "request", "client"))
        .build();
    static final SpanAttachedEvent CLIENT_WRITE = zipkinEvent(
        TRACE, CLIENT, MIDNIGHT_SECOND - 29, 0, "Syscall write", "package_size", "182B", "package_count", "1")
        .addSummary(KeyIntValuePair.newBuilder().setKey("package_bytes").setValue(182L)).build();
    static final SpanAttachedEvent SERVER_REQUEST = zipkinEvent(
        TRACE, CLIENT, MIDNIGHT_SECOND - 28, 0, "HTTP Request Sampling", http("inbound", "request", "server"))
        .build();
    static final SpanAttachedEvent DATABASE_SEND = zipkinEvent(
        TRACE, DATABASE, MIDNIGHT_SECOND - 25, 0, "Syscall sendto", "package_size", "96B", "package_count", "1")
        .build();
    static final SpanAttachedEvent UNKNOWN_SPAN_READ = zipkinEvent(
        TRACE, "ffffffffffffffff", MIDNIGHT_SECOND - 24, 0, "Syscall read", "package_size", "64B",
        "package_count", "1").build();
    static final SpanAttachedEvent BACKEND_REQUEST = zipkinEvent(
        TRACE, SERVER, MIDNIGHT_SECOND - 23, 0, "HTTP Request Sampling", http("outbound", "request", "client"))
        .build();
    static final SpanAttachedEvent INVENTORY_REQUEST = zipkinEvent(
        TRACE, SERVER, MIDNIGHT_SECOND - 22, 0, "HTTP Request Sampling", http("inbound", "request", "server"))
        .build();
    static final SpanAttachedEvent INVENTORY_RESPONSE = zipkinEvent(
        TRACE, SERVER, MIDNIGHT_SECOND - 21, 0, "HTTP Response Sampling", http("outbound", "response", "server"))
        .build();
    static final SpanAttachedEvent BACKEND_RESPONSE = zipkinEvent(
        TRACE, SERVER, MIDNIGHT_SECOND - 21, 500_000_000, "HTTP Response Sampling",
        http("inbound", "response", "client")).build();
    static final SpanAttachedEvent SERVER_RESPONSE = zipkinEvent(
        TRACE, CLIENT, MIDNIGHT_SECOND - 20, 0, "HTTP Response Sampling", http("outbound", "response", "server"))
        .build();
    static final SpanAttachedEvent CLIENT_RESPONSE = zipkinEvent(
        TRACE, CLIENT, MIDNIGHT_SECOND - 19, 0, "HTTP Response Sampling", http("inbound", "response", "client"))
        .build();
    static final SpanAttachedEvent LOAD_WRITE = zipkinEvent(
        ONE_SERVICE_TRACE, LOAD, MIDNIGHT_SECOND - 18, 0, "Syscall write", "package_size", "1KB",
        "package_count", "2").build();
    private static final SpanAttachedEvent UNQUERIED_TRACE_REQUEST = zipkinEvent(
        UNQUERIED_TRACE, CLIENT, MIDNIGHT_SECOND - 27, 0, "HTTP Request Sampling",
        http("outbound", "request", "client")).build();
    static final SpanAttachedEvent SAME_SPAN_ID_REQUEST = zipkinEvent(
        SAME_SPAN_ID_TRACE, "3000000000000001", MIDNIGHT_SECOND - 40, 0, "HTTP Request Sampling",
        http("outbound", "request", "client")).build();
    static final SpanAttachedEvent OTHER_SAME_SPAN_ID_REQUEST = zipkinEvent(
        OTHER_SAME_SPAN_ID_TRACE, "3000000000000001", MIDNIGHT_SECOND - 40, 0, "HTTP Request Sampling",
        http("outbound", "request", "client")).build();
    static final SpanAttachedEvent FIRST_SPAN_REQUEST = zipkinEvent(
        TWO_SPANS_TRACE, "5000000000000001", MIDNIGHT_SECOND - 41, 0, "HTTP Request Sampling",
        http("outbound", "request", "client")).build();
    static final SpanAttachedEvent SECOND_SPAN_REQUEST = zipkinEvent(
        TWO_SPANS_TRACE, "5000000000000002", MIDNIGHT_SECOND - 41, 0, "HTTP Request Sampling",
        http("outbound", "request", "client")).build();
    /** Stored with the SkyWalking events, as its trace context's type says, though it names this trace. */
    private static final SpanAttachedEvent SKYWALKING_TYPED_WRITE = event(
        SpanAttachedEvent.SpanReferenceType.SKYWALKING, TRACE, "3a5b7c9d0e1f.31.17368992000000021", "0",
        MIDNIGHT_SECOND - 26, 0, "Syscall write", "package_size", "182B", "package_count", "1").build();

    /** Neither in start order nor against it. */
    static final List<SpanAttachedEvent> EVENTS = Arrays.asList(
        SERVER_RESPONSE, LOAD_WRITE, INVENTORY_REQUEST, SAME_SPAN_ID_REQUEST, SECOND_SPAN_REQUEST, CLIENT_WRITE,
        BACKEND_RESPONSE, UNKNOWN_SPAN_READ, SKYWALKING_TYPED_WRITE, CLIENT_REQUEST, OTHER_SAME_SPAN_ID_REQUEST,
        FIRST_SPAN_REQUEST, BACKEND_REQUEST,
        UNQUERIED_TRACE_REQUEST, CLIENT_RESPONSE, INVENTORY_RESPONSE, DATABASE_SEND, SERVER_REQUEST);
    static final List<SpanAttachedEvent> EVENTS_OF_BOTH_TRACES = Arrays.asList(
        CLIENT_REQUEST, CLIENT_WRITE, SERVER_REQUEST, DATABASE_SEND, UNKNOWN_SPAN_READ, BACKEND_REQUEST,
        INVENTORY_REQUEST, INVENTORY_RESPONSE, BACKEND_RESPONSE, SERVER_RESPONSE, CLIENT_RESPONSE, LOAD_WRITE);

    private ZipkinTraceFixture() {
    }

    /**
     * The traces with each event attached to its span as {@code ZipkinQueryService} shows it: a start and a finish
     * annotation, and the event's tags and summary as tags. An event name that occurs more than once in a trace is
     * numbered in start order.
     */
    static List<List<Span>> expectedTraces(final String... traceIds) {
        final List<List<Span>> traces = new ArrayList<>();
        for (final String traceId : traceIds) {
            if (TRACE.equals(traceId)) {
                traces.add(Arrays.asList(
                    ROOT_SPAN,
                    attach(attach(attach(CLIENT_SPAN.toBuilder(),
                                         "HTTP Request Sampling-1", CLIENT_REQUEST),
                                  "Syscall write", CLIENT_WRITE),
                           "HTTP Response Sampling-4", CLIENT_RESPONSE).build(),
                    // The server side of the frontend's call, and the client side of its own call to the inventory.
                    attach(attach(attach(attach(SERVER_SPAN.toBuilder(),
                                                "HTTP Request Sampling-2", SERVER_REQUEST),
                                         "HTTP Request Sampling-3", BACKEND_REQUEST),
                                  "HTTP Response Sampling-2", BACKEND_RESPONSE),
                           "HTTP Response Sampling-3", SERVER_RESPONSE).build(),
                    attach(DATABASE_SPAN.toBuilder(), "Syscall sendto", DATABASE_SEND).build(),
                    attach(attach(INVENTORY_SPAN.toBuilder(),
                                  "HTTP Request Sampling-4", INVENTORY_REQUEST),
                           "HTTP Response Sampling-1", INVENTORY_RESPONSE).build()));
            } else {
                traces.add(Arrays.asList(
                    JOB_SPAN,
                    attach(LOAD_SPAN.toBuilder(), "Syscall write", LOAD_WRITE).build()));
            }
        }
        return canonical(traces);
    }

    /**
     * The traces sorted by id, each trace's spans sorted by id, and every event's YAML tag read as a map: the Zipkin
     * API promises no order of traces or spans, and YAML has more than one way to write a map.
     */
    static List<List<Span>> canonical(final List<List<Span>> traces) {
        final List<List<Span>> canonical = new ArrayList<>();
        for (final List<Span> trace : traces) {
            final List<Span> spans = new ArrayList<>();
            for (final Span span : trace) {
                final Span.Builder builder = span.toBuilder().clearTags();
                for (final Map.Entry<String, String> tag : span.tags().entrySet()) {
                    final boolean eventMap = tag.getKey().endsWith(".tags") || tag.getKey().endsWith(".summary");
                    builder.putTag(tag.getKey(), eventMap ? sortedMap(new Yaml().load(tag.getValue())) : tag.getValue());
                }
                spans.add(builder.build());
            }
            spans.sort(Comparator.comparing(Span::id));
            canonical.add(spans);
        }
        canonical.sort(Comparator.comparing(trace -> trace.isEmpty() ? "" : trace.get(0).traceId()));
        return canonical;
    }

    private static Span.Builder attach(final Span.Builder span, final String name, final SpanAttachedEvent event) {
        span.addAnnotation(micros(event.getStartTime()), "Start " + name);
        span.addAnnotation(micros(event.getEndTime()), "Finished " + name);
        if (event.getSummaryCount() > 0) {
            final Map<String, Long> summary = new TreeMap<>();
            for (final KeyIntValuePair pair : event.getSummaryList()) {
                summary.put(pair.getKey(), pair.getValue());
            }
            span.putTag(eventTagKey(name, "summary"), new Yaml().dump(summary));
        }
        if (event.getTagsCount() > 0) {
            final Map<String, String> tags = new TreeMap<>();
            for (final KeyStringValuePair tag : event.getTagsList()) {
                tags.put(tag.getKey(), tag.getValue());
            }
            span.putTag(eventTagKey(name, "tags"), new Yaml().dump(tags));
        }
        return span;
    }

    private static String eventTagKey(final String eventName, final String suffix) {
        return (eventName + "." + suffix).replace(' ', '.').toLowerCase(Locale.ROOT);
    }

    private static long micros(final Instant instant) {
        return TimeUnit.SECONDS.toMicros(instant.getSeconds()) + TimeUnit.NANOSECONDS.toMicros(instant.getNanos());
    }

    private static String sortedMap(final Map<?, ?> map) {
        final Map<String, String> sorted = new TreeMap<>();
        map.forEach((key, value) -> sorted.put(String.valueOf(key), String.valueOf(value)));
        return sorted.toString();
    }

    private static SpanAttachedEvent.Builder zipkinEvent(final String traceId, final String spanId,
                                                         final long startSecond, final int startNanos,
                                                         final String name, final String... tags) {
        return event(SpanAttachedEvent.SpanReferenceType.ZIPKIN, traceId, "", spanId, startSecond, startNanos,
                     name, tags);
    }

    private static Span.Builder span(final String traceId, final String spanId, final String parentId,
                                     final Span.Kind kind, final String name, final String service,
                                     final long timestampMillis, final long durationMicros) {
        return Span.newBuilder()
                   .traceId(traceId)
                   .id(spanId)
                   .parentId(parentId)
                   .kind(kind)
                   .name(name)
                   .localEndpoint(Endpoint.newBuilder().serviceName(service).build())
                   .timestamp(timestampMillis * 1000L)
                   .duration(durationMicros)
                   .debug(false)
                   .shared(false);
    }
}
