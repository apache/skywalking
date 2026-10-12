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

package org.apache.skywalking.oap.server.core.trace;

import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin2.Span;

/**
 * The derived, protocol-neutral view of one span that a TAL rule reads as {@code meta.*} and its extractor may
 * overwrite. It is the span's counterpart of {@link org.apache.skywalking.oap.server.core.source.LogMetadata}.
 *
 * <p>Receivers build it from their own wire object: the OTLP receiver from the protobuf span, which this module
 * cannot see (see {@link OTLPSpanReader}), and the Zipkin receiver through {@link #of(Span)}.
 */
@Data
@Builder
public class SpanMetadata {
    public static final String KIND_UNSPECIFIED = "UNSPECIFIED";
    public static final String KIND_SERVER = "SERVER";
    public static final String KIND_CLIENT = "CLIENT";
    public static final String KIND_PRODUCER = "PRODUCER";
    public static final String KIND_CONSUMER = "CONSUMER";
    public static final String STATUS_UNSET = "UNSET";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    private static final String KIND_PREFIX = "SPAN_KIND_";
    private static final String STATUS_PREFIX = "STATUS_CODE_";
    /**
     * The name {@code SpanForward} gives a Zipkin span whose local endpoint carries no service name.
     */
    private static final String UNKNOWN_SERVICE = "Unknown";
    /**
     * The Zipkin convention for a failed span, read by {@link #of(Span)}.
     */
    private static final String ZIPKIN_ERROR_TAG = "error";

    @Builder.Default
    private String service = "";
    @Builder.Default
    private String serviceInstance = "";
    @Builder.Default
    private String endpoint = "";
    @Builder.Default
    private String layer = "";
    @Builder.Default
    private String kind = KIND_UNSPECIFIED;
    @Builder.Default
    private String statusCode = STATUS_UNSET;
    @Builder.Default
    private String statusMessage = "";
    /**
     * Milliseconds since epoch.
     */
    private long startTime;
    /**
     * The span's end, in milliseconds since epoch; the default time bucket of every metric the rule emits.
     */
    private long timestamp;
    /**
     * End minus start in milliseconds, clamped to a non-negative int as {@code setPublicAttrs} does for agents.
     */
    private int latency;
    @Builder.Default
    private String traceId = "";
    @Builder.Default
    private String spanId = "";
    @Builder.Default
    private String parentSpanId = "";
    private boolean root;
    @Builder.Default
    private String scopeName = "";
    @Builder.Default
    private String scopeVersion = "";

    /**
     * @param protoEnumName the OTLP {@code SpanKind} enum name, such as {@code SPAN_KIND_CLIENT}
     * @return the name without its prefix, {@code UNSPECIFIED} when the name is unrecognized
     */
    public static String normalizeKind(final String protoEnumName) {
        if (StringUtil.isEmpty(protoEnumName)) {
            return KIND_UNSPECIFIED;
        }
        return protoEnumName.startsWith(KIND_PREFIX) ? protoEnumName.substring(KIND_PREFIX.length()) : protoEnumName;
    }

    /**
     * @param protoEnumName the OTLP {@code StatusCode} enum name, such as {@code STATUS_CODE_ERROR}
     * @return the name without its prefix, {@code UNSET} when the name is unrecognized
     */
    public static String normalizeStatusCode(final String protoEnumName) {
        if (StringUtil.isEmpty(protoEnumName)) {
            return STATUS_UNSET;
        }
        return protoEnumName.startsWith(STATUS_PREFIX)
            ? protoEnumName.substring(STATUS_PREFIX.length())
            : protoEnumName;
    }

    /**
     * @param startNanos the span's start, nanoseconds since epoch
     * @param endNanos   the span's end, nanoseconds since epoch
     * @return the duration in milliseconds, clamped to {@code [0, Integer.MAX_VALUE]}
     */
    public static int latencyMillis(final long startNanos, final long endNanos) {
        final long millis = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, endNanos - startNanos));
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    /**
     * The Zipkin adapter. Zipkin carries no resource, no instrumentation scope and no instance identity, so those
     * fields stay empty and {@code layer: auto} rules are the ones that see the span.
     *
     * @param span the span as it arrived over the Zipkin protocol
     * @return the metadata view of it
     */
    public static SpanMetadata of(final Span span) {
        final long startMillis = TimeUnit.MICROSECONDS.toMillis(span.timestampAsLong());
        final long durationMillis = TimeUnit.MICROSECONDS.toMillis(span.durationAsLong());
        final String error = span.tags().get(ZIPKIN_ERROR_TAG);
        return SpanMetadata.builder()
                           .service(StringUtil.isEmpty(span.localServiceName()) ? UNKNOWN_SERVICE
                               : span.localServiceName())
                           .endpoint(span.name() == null ? "" : span.name())
                           .kind(span.kind() == null ? KIND_UNSPECIFIED : span.kind().name())
                           .statusCode(error == null ? STATUS_UNSET : STATUS_ERROR)
                           .statusMessage(error == null ? "" : error)
                           .startTime(startMillis)
                           .timestamp(startMillis + durationMillis)
                           .latency((int) Math.min(durationMillis, Integer.MAX_VALUE))
                           .traceId(span.traceId())
                           .spanId(span.id())
                           .parentSpanId(span.parentId() == null ? "" : span.parentId())
                           .root(span.parentId() == null)
                           .build();
    }
}
