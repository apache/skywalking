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

package org.apache.skywalking.oap.query.traceql.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Path;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.entity.BuildInfoResponse;
import org.apache.skywalking.oap.query.traceql.entity.ErrorResponse;
import org.apache.skywalking.oap.query.traceql.entity.QueryResponse;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.joda.time.DateTime;

/**
 * Handler for Grafana Tempo API endpoints.
 * Implements the Tempo API specification for trace querying and search.
 *
 * Error Handling:
 * - 200 OK: Successful request
 * - 400 Bad Request: Invalid request parameters (malformed TraceQL query, invalid duration format, etc.)
 */
public abstract class TraceQLApiHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String SCOPE_RESOURCE = "resource";
    public static final String SCOPE_SPAN = "span";

    // Intrinsic tag names
    public static final String STATUS = "status";
    public static final String NAME = "name";

    // Resource-scoped tag names
    public static final String RESOURCE_SERVICE_NAME = "resource.service.name";
    public static final String RESOURCE_SERVICE = "resource.service";
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE = "service";

    // Span-scoped tag prefix and names
    public static final String SPAN_PREFIX = "span.";
    public static final String SPAN_NAME = "span.name";
    public static final String SPAN_KIND = "span.kind";

    // Status values
    public static final String ERROR = "error";
    public static final String OK = "ok";
    public static final String TYPE_STRING = "string";

    @Get
    @Path("/ready")
    public HttpResponse ready() throws IOException {
        return HttpResponse.of("ready");
    }

    /**
     * Echo endpoint for testing API connectivity.
     * GET /api/echo
     */
    @Get
    @Path("/api/echo")
    public HttpResponse echo() throws IOException {
        return HttpResponse.of("echo");
    }

    /**
     * Returns build information about the Tempo instance.
     * GET /api/status/buildinfo
     */
    @Get
    @Path("/api/status/buildinfo")
    public HttpResponse buildinfo() {
        BuildInfoResponse buildInfo = BuildInfoResponse.builder()
            .version("v2.9.0")
            .revision("")
            .branch("")
            .buildUser("")
            .buildDate("")
            .goVersion("")
            .build();
        return HttpResponse.ofJson(buildInfo);
    }

    /**
     * Query trace by trace ID.
     * GET /api/traces/{traceId}
     *
     * @param traceId The trace ID
     * @return Trace data in OTLP format
     */
    @Get
    @Path("/api/traces/{traceId}")
    public HttpResponse queryTraceV1(@Param("traceId") String traceId,
                                     @Param("start") Optional<Long> start,
                                     @Param("end") Optional<Long> end,
                                     @Header("Accept") Optional<String> accept) throws IOException, DecoderException {
        return queryTrace(traceId, start, end, accept);
    }

    /**
     * Query trace by trace ID.
     * GET /api/v2/traces/{traceId}
     *
     * @param traceId The trace ID, for SkyWalking native traces, the original traceId has to be hex encoding of the UTF-8 bytes
     *                you can get the encoded traceId from traces list query API `/api/search`.
     * @param accept Accept header for response format
     * @return Trace data in OTLP format
     */
    @Get
    @Path("/api/v2/traces/{traceId}")
    public HttpResponse queryTrace(@Param("traceId") String traceId,
                                   @Param("start") Optional<Long> start,
                                   @Param("end") Optional<Long> end,
                                   @Header("Accept") Optional<String> accept) throws IOException, DecoderException {
        return queryTraceImpl(traceId, accept);
    }

    /**
     * Abstract method to be implemented by subclasses for trace query logic.
     */
    protected abstract HttpResponse queryTraceImpl(String traceId, Optional<String> accept) throws IOException, DecoderException;

    /**
     * Search for traces matching the given criteria.
     * GET /api/search
     *
     * @param query       TraceQL query or tag query
     * @param tags        Deprecated tag query format
     * @param minDuration Minimum trace duration
     * @param maxDuration Maximum trace duration
     * @param limit       Maximum number of traces to return
     * @param start       Start of time range (Unix epoch seconds)
     * @param end         End of time range (Unix epoch seconds)
     * @param spss        Spans per span set
     * @return Search results with matching traces
     */
    @Get
    @Path("/api/search")
    public HttpResponse search(@Param("q") Optional<String> query,
                               @Param("tags") Optional<String> tags,
                               @Param("minDuration") Optional<String> minDuration,
                               @Param("maxDuration") Optional<String> maxDuration,
                               @Param("limit") Optional<Integer> limit,
                               @Param("start") Optional<Long> start,
                               @Param("end") Optional<Long> end,
                               @Param("spss") Optional<Integer> spss) throws IOException {
        return searchImpl(query, tags, minDuration, maxDuration, limit, start, end, spss);
    }

    /**
     * Abstract method to be implemented by subclasses for search logic.
     */
    protected abstract HttpResponse searchImpl(
        Optional<String> query,
        Optional<String> tags,
        Optional<String> minDuration,
        Optional<String> maxDuration,
        Optional<Integer> limit,
        Optional<Long> start,
        Optional<Long> end,
        Optional<Integer> spss) throws IOException;

    /**
     * Get all discovered tag names (v1).
     * GET /api/search/tags
     *
     * @param scope Scope to filter tags (intrinsic/resource/span/none)
     * @param limit Maximum number of tags to return
     * @param start Start of time range
     * @param end   End of time range
     * @return List of tag names
     */
    @Get
    @Path("/api/search/tags")
    public HttpResponse searchTags(
        @Param("scope") Optional<String> scope,
        @Param("limit") Optional<Integer> limit,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end) throws IOException {
        return searchTagsImpl(scope, limit, start, end);
    }

    /**
     * Abstract method to be implemented by subclasses for tag search logic.
     */
    protected abstract HttpResponse searchTagsImpl(
        Optional<String> scope,
        Optional<Integer> limit,
        Optional<Long> start,
        Optional<Long> end) throws IOException;

    /**
     * Get all discovered tag names (v2).
     * GET /api/v2/search/tags
     *
     * @param q     Optional TraceQL query to filter which tags to return
     * @param scope Scope to filter tags (intrinsic/resource/span/none)
     * @param limit Maximum number of tags to return
     * @param start Start of time range (Unix epoch seconds)
     * @param end   End of time range (Unix epoch seconds)
     * @return List of tag names with type information
     */
    @Get
    @Path("/api/v2/search/tags")
    public HttpResponse searchTagsV2(
        @Param("q") Optional<String> q,
        @Param("scope") Optional<String> scope,
        @Param("limit") Optional<Integer> limit,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end) throws IOException {
        return searchTagsV2Impl(q, scope, limit, start, end);
    }

    /**
     * Abstract method to be implemented by subclasses for tag search v2 logic.
     */
    protected abstract HttpResponse searchTagsV2Impl(
        Optional<String> q,
        Optional<String> scope,
        Optional<Integer> limit,
        Optional<Long> start,
        Optional<Long> end) throws IOException;

    /**
     * Get all discovered values for a given tag (v1).
     * GET /api/search/tag/{tagName}/values
     *
     * @param tagName Name of the tag to search values for
     * @param limit   Maximum number of values to return
     * @param start   Start of time range
     * @param end     End of time range
     * @return List of tag values
     */
    @Get
    @Path("/api/search/tag/{tagName}/values")
    public HttpResponse searchTagValuesV1(
        @Param("tagName") String tagName,
        @Param("limit") Optional<Integer> limit,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end,
        @Param("maxStaleValues") Optional<Integer> maxStaleValues // not supported in OAP
    ) throws IOException {
        return searchTagValuesImpl(tagName, Optional.empty(), limit, start, end);
    }

    /**
     * Get all discovered values for a given tag (v2).
     * GET /api/v2/search/tag/{tagName}/values
     *
     * @param tagName Name of the tag to search values for
     * @param query   Optional TraceQL filter query
     * @param limit   Maximum number of values to return
     * @param start   Start of time range
     * @param end     End of time range
     * @return List of tag values with type information
     */
    @Get
    @Path("/api/v2/search/tag/{tagName}/values")
    public HttpResponse searchTagValues(
        @Param("tagName") String tagName,
        @Param("q") Optional<String> query,
        @Param("limit") Optional<Integer> limit,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end,
        @Param("maxStaleValues") Optional<Integer> maxStaleValues // not supported in OAP
    ) throws IOException {
        return searchTagValuesImpl(tagName, query, limit, start, end);
    }

    /**
     * Query metrics over a time range.
     * GET /api/metrics/query_range
     *
     * @param query PromQL-like query string
     * @param start Start timestamp
     * @param end   End timestamp
     * @param step  Query resolution step width
     * @return Metrics data over time range
     */
    @Get
    @Path("/api/metrics/query_range")
    public HttpResponse metricsQueryRange(
        @Param("query") String query,
        @Param("start") Long start,
        @Param("end") Long end,
        @Param("step") Optional<String> step) throws IOException {
        return HttpResponse.ofJson("Not supported.");
    }

    /**
     * Execute an instant metrics query.
     * GET /api/metrics/query
     *
     * @param query PromQL-like query string
     * @param time  Evaluation timestamp
     * @return Instant metrics data
     */
    @Get
    @Path("/api/metrics/query")
    public HttpResponse metricsQuery(
        @Param("query") String query,
        @Param("time") Optional<Long> time) throws IOException {
        return HttpResponse.ofJson("Not supported.");
    }

    /**
     * Abstract method to be implemented by subclasses for tag value search logic.
     */
    protected abstract HttpResponse searchTagValuesImpl(
        String tagName,
        Optional<String> query,
        Optional<Integer> limit,
        Optional<Long> start,
        Optional<Long> end) throws IOException;

    /**
     * Create a successful HTTP response with JSON content.
     */
    protected HttpResponse successResponse(QueryResponse response) throws JsonProcessingException {
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
                           .contentType(MediaType.JSON)
                           .build(),
            HttpData.ofUtf8(MAPPER.writeValueAsString(response))
        );
    }

    /**
     * Create an error response with appropriate HTTP status code.
     *
     * @param status HTTP status code
     * @param message Error message
     * @return HTTP response with error details
     */
    protected HttpResponse errorResponse(HttpStatus status, String message) throws JsonProcessingException {
        ErrorResponse error = ErrorResponse.builder()
            .error(message)
            .build();
        return HttpResponse.of(
            ResponseHeaders.builder(status)
                           .contentType(MediaType.JSON)
                           .build(),
            HttpData.ofUtf8(MAPPER.writeValueAsString(error))
        );
    }

    /**
     * Create a 400 Bad Request error response.
     * Used for invalid request parameters like malformed TraceQL queries.
     */
    protected HttpResponse badRequestResponse(String message) throws JsonProcessingException {
        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Build Duration object from start and end timestamps.
     *
     * @param start Optional start timestamp in seconds
     * @param end Optional end timestamp in seconds
     * @param defaultLookback Default lookback duration in milliseconds if start is not provided
     * @return Duration object with start and end times
     */
    protected Duration buildDuration(Optional<Long> start, Optional<Long> end, long defaultLookback) {
        Duration duration = new Duration();

        long endTime;
        long startTime;

        if (end.isPresent()) {
            endTime = end.get() * 1000;
        } else {
            endTime = System.currentTimeMillis();
        }

        if (start.isPresent()) {
            startTime = start.get() * 1000;
        } else {
            startTime = endTime - defaultLookback;
        }

        duration.setStart(new DateTime(startTime).toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(new DateTime(endTime).toString("yyyy-MM-dd HHmmss"));
        duration.setStep(Step.SECOND);

        return duration;
    }

    /**
     * Build HTTP response with Protobuf content.
     */
    protected HttpResponse buildProtobufHttpResponse(TraceByIDResponse protoResponse) {
        byte[] protoBytes = protoResponse.toByteArray();
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
                           .contentType(MediaType.parse("application/protobuf"))
                           .build(),
            HttpData.wrap(protoBytes)
        );
    }
}

