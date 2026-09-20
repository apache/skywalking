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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.entity.BuildInfoResponse;
import org.apache.skywalking.oap.query.traceql.entity.ErrorResponse;
import org.apache.skywalking.oap.query.traceql.entity.QueryResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesV2Response;
import org.apache.skywalking.oap.query.traceql.entity.TagValuesResponse;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLParseResult;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParser;
import org.apache.skywalking.oap.server.library.util.StringUtil;
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
 * - 501 Not Implemented: TraceQL metrics endpoints, which no datasource serves
 */
public abstract class TraceQLApiHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String SCOPE_RESOURCE = "resource";
    public static final String SCOPE_SPAN = "span";

    // Intrinsic tag names
    public static final String STATUS = "status";
    public static final String NAME = "name";
    public static final String KIND = "kind";
    public static final String DURATION = "duration";
    /**
     * Tempo's scoped spelling of the span intrinsics, {@code span:kind}; accepted wherever the plain one is.
     */
    public static final String SPAN_INTRINSIC_PREFIX = "span:";
    /**
     * The tag the visitor stores {@code span.http.status_code} filters under, the pre-1.23 semantic-convention
     * spelling that the SkyWalking agents and Zipkin instrumentations write; every datasource filters it as a tag.
     */
    public static final String HTTP_STATUS_CODE = "http.status_code";

    // Resource-scoped tag names
    public static final String RESOURCE_SERVICE_NAME = "resource.service.name";
    public static final String RESOURCE_SERVICE = "resource.service";
    public static final String RESOURCE_REMOTE_SERVICE = "resource.remote.service";
    public static final String RESOURCE_INSTANCE = "resource.instance";
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE = "service";
    public static final String REMOTE_SERVICE = "remote.service";
    public static final String INSTANCE = "instance";
    public static final String SERVICE_INSTANCE_ID = "service.instance.id";
    /**
     * The keys an unscoped {@code .key} tag name resolves to the resource scope; see {@link #normalizeTagName}.
     */
    private static final List<String> RESOURCE_TAG_KEYS = Arrays.asList(
        SERVICE_NAME, SERVICE, REMOTE_SERVICE, INSTANCE, SERVICE_INSTANCE_ID);

    // Span-scoped tag prefix and names
    public static final String SPAN_PREFIX = "span.";
    public static final String SPAN_NAME = "span.name";
    public static final String SPAN_KIND = "span.kind";

    // Status values
    public static final String ERROR = "error";
    public static final String OK = "ok";
    public static final String TYPE_STRING = "string";
    // Tempo's scopes on the tag endpoints
    public static final String SCOPE_INTRINSIC = "intrinsic";
    public static final String SCOPE_EVENT = "event";
    public static final String SCOPE_LINK = "link";
    public static final String SCOPE_INSTRUMENTATION = "instrumentation";
    private static final String METRICS_NOT_SUPPORTED =
        "TraceQL metrics are not supported: the trace stores answer trace and span queries, not aggregations.";
    protected static final List<String> ALL_SCOPES = Arrays.asList(SCOPE_RESOURCE, SCOPE_SPAN, SCOPE_INTRINSIC);
    /**
     * Scopes Tempo defines but no datasource has names for: events, links and the instrumentation scope are stored
     * inside the span, not indexed.
     */
    protected static final List<String> EMPTY_SCOPES = Arrays.asList(SCOPE_EVENT, SCOPE_LINK, SCOPE_INSTRUMENTATION);
    /**
     * Tempo's default for {@code spss}, the spans listed per span set.
     */
    private static final int DEFAULT_SPANS_PER_SPAN_SET = 3;
    /**
     * The most matching traces a tag lookup filtered by {@code q} samples. Tempo's filtered lookups are samples too,
     * bounded by bytes inspected rather than traces; the answer feeds a dropdown, not a report.
     */
    protected static final int TAG_FILTER_SAMPLE_TRACES = 50;

    public static final String ALL = "*";

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
                                     @Param("coldStage") Optional<Boolean> coldStage,
                                     @Header("Accept") Optional<String> accept) throws IOException, DecoderException {
        return queryTrace(traceId, start, end, coldStage, accept);
    }

    /**
     * Query trace by trace ID.
     * GET /api/v2/traces/{traceId}
     *
     * @param traceId The trace ID, for SkyWalking native traces, the original traceId has to be hex encoding of the UTF-8 bytes
     *                you can get the encoded traceId from traces list query API `/api/search`.
     * @param coldStage A SkyWalking addition, honoured by the OTLP datasource on BanyanDB: {@code true} reads the
     *                  cold stage instead of the hot and warm stages, and needs {@code start} and {@code end}.
     * @param accept Accept header for response format
     * @return Trace data in OTLP format
     */
    @Get
    @Path("/api/v2/traces/{traceId}")
    public HttpResponse queryTrace(@Param("traceId") String traceId,
                                   @Param("start") Optional<Long> start,
                                   @Param("end") Optional<Long> end,
                                   @Param("coldStage") Optional<Boolean> coldStage,
                                   @Header("Accept") Optional<String> accept) throws IOException, DecoderException {
        return queryTraceImpl(traceId, start, end, coldStage, accept);
    }

    /**
     * Abstract method to be implemented by subclasses for trace query logic.
     */
    protected abstract HttpResponse queryTraceImpl(String traceId,
                                                   Optional<Long> start,
                                                   Optional<Long> end,
                                                   Optional<Boolean> coldStage,
                                                   Optional<String> accept) throws IOException, DecoderException;

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
     * @param coldStage   A SkyWalking addition, honoured by the OTLP datasource on BanyanDB: {@code true} searches
     *                    the cold stage instead of the hot and warm stages
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
                               @Param("spss") Optional<Integer> spss,
                               @Param("coldStage") Optional<Boolean> coldStage) throws IOException {
        return searchImpl(query, tags, minDuration, maxDuration, limit, start, end, spss, coldStage);
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
        Optional<Integer> spss,
        Optional<Boolean> coldStage) throws IOException;

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
     * TraceQL metrics over a time range, GET /api/metrics/query_range. No datasource supports it: a metrics query
     * aggregates every span of the window at query time, which the trace stores do not offer, so the answer is 501
     * with an error body rather than a 200 Grafana cannot parse.
     *
     * @param q         Tempo's name for the TraceQL metrics query
     * @param query     the spelling this handler accepted before Tempo's, kept for callers that used it
     * @param start     start of the range in unix seconds
     * @param end       end of the range in unix seconds
     * @param since     Tempo's relative range, a duration
     * @param step      the resolution step, a duration
     * @param exemplars the number of exemplars asked for
     * @return 501 with an {@link ErrorResponse}
     */
    @Get
    @Path("/api/metrics/query_range")
    public HttpResponse metricsQueryRange(
        @Param("q") Optional<String> q,
        @Param("query") Optional<String> query,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end,
        @Param("since") Optional<String> since,
        @Param("step") Optional<String> step,
        @Param("exemplars") Optional<Integer> exemplars) throws IOException {
        return errorResponse(HttpStatus.NOT_IMPLEMENTED, METRICS_NOT_SUPPORTED);
    }

    /**
     * Instant TraceQL metrics, GET /api/metrics/query. Not supported, see {@link #metricsQueryRange}.
     *
     * @param q     Tempo's name for the TraceQL metrics query
     * @param query the spelling this handler accepted before Tempo's, kept for callers that used it
     * @param start start of the range in unix seconds
     * @param end   end of the range in unix seconds
     * @param since Tempo's relative range, a duration
     * @param time  the evaluation time this handler accepted before
     * @return 501 with an {@link ErrorResponse}
     */
    @Get
    @Path("/api/metrics/query")
    public HttpResponse metricsQuery(
        @Param("q") Optional<String> q,
        @Param("query") Optional<String> query,
        @Param("start") Optional<Long> start,
        @Param("end") Optional<Long> end,
        @Param("since") Optional<String> since,
        @Param("time") Optional<Long> time) throws IOException {
        return errorResponse(HttpStatus.NOT_IMPLEMENTED, METRICS_NOT_SUPPORTED);
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
     * The tag names a datasource has for one scope, {@link #SCOPE_RESOURCE}, {@link #SCOPE_SPAN} or
     * {@link #SCOPE_INTRINSIC}.
     */
    @FunctionalInterface
    protected interface TagNamesSource {
        List<String> namesOf(String scope) throws IOException;
    }

    /**
     * Tempo's v1 tag-name list: the names of every requested scope in one flat list, capped by {@code limit}.
     */
    protected HttpResponse tagNames(final Optional<String> scope,
                                    final Optional<Integer> limit,
                                    final TagNamesSource source) throws IOException {
        final List<String> names = new ArrayList<>();
        try {
            for (final String requested : requestedScopes(scope)) {
                names.addAll(source.namesOf(requested));
            }
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
        final TagNamesResponse response = new TagNamesResponse();
        response.getTagNames().addAll(truncate(names, limit));
        return successResponse(response);
    }

    /**
     * Tempo's v2 tag-name list: one entry per requested scope, {@code limit} applied to each scope on its own, as
     * Tempo documents it.
     */
    protected HttpResponse tagNamesV2(final Optional<String> scope,
                                      final Optional<Integer> limit,
                                      final TagNamesSource source) throws IOException {
        final TagNamesV2Response response = new TagNamesV2Response();
        try {
            for (final String requested : requestedScopes(scope)) {
                final TagNamesV2Response.Scope entry = new TagNamesV2Response.Scope(requested);
                entry.getTags().addAll(truncate(source.namesOf(requested), limit));
                response.getScopes().add(entry);
            }
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
        return successResponse(response);
    }

    /**
     * The scopes a tag-name request covers: all three when {@code scope} is absent, the one asked for otherwise.
     * Tempo's event, link and instrumentation scopes are valid requests with no names here.
     */
    protected static List<String> requestedScopes(final Optional<String> scope) {
        if (!scope.isPresent() || scope.get().isEmpty()) {
            return ALL_SCOPES;
        }
        final String requested = scope.get().toLowerCase(Locale.ROOT);
        if (ALL_SCOPES.contains(requested)) {
            return Collections.singletonList(requested);
        }
        if (EMPTY_SCOPES.contains(requested)) {
            return Collections.emptyList();
        }
        throw new IllegalArgumentException("Unsupported scope: " + scope.get() + ", expected one of "
                                               + ALL_SCOPES + " or " + EMPTY_SCOPES + ".");
    }

    protected static <T> List<T> truncate(final Collection<T> values, final Optional<Integer> limit) {
        final int max = limit.filter(value -> value > 0).orElse(Integer.MAX_VALUE);
        final List<T> list = values instanceof List ? (List<T>) values : new ArrayList<>(values);
        return list.size() > max ? list.subList(0, max) : list;
    }

    protected static TagValuesResponse stringValues(final Collection<String> values, final Optional<Integer> limit) {
        final TagValuesResponse response = new TagValuesResponse();
        for (final String value : truncate(values, limit)) {
            response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, value));
        }
        return response;
    }

    /**
     * The parsed {@code q} of a tag lookup: null when the parameter is absent or blank, a result carrying an error
     * when the TraceQL does not parse.
     */
    protected static TraceQLParseResult parseFilter(final Optional<String> q) {
        if (!q.isPresent() || StringUtil.isBlank(q.get())) {
            return null;
        }
        return TraceQLQueryParser.extractParams(q.get());
    }

    /**
     * True when the filter carries a condition. Grafana sends {@code {}} when nothing is selected yet, which asks
     * for the unfiltered catalogs.
     */
    protected static boolean hasFilter(final TraceQLQueryParams params) {
        return isSet(params.getServiceName()) || isSet(params.getRemoteServiceName()) || isSet(params.getServiceInstance())
            || isSet(params.getSpanName()) || params.getMinDuration() != null || params.getMaxDuration() != null
            || StringUtil.isNotBlank(params.getStatus()) || StringUtil.isNotBlank(params.getKind())
            || StringUtil.isNotBlank(params.getHttpStatusCode())
            || (params.getTags() != null && !params.getTags().isEmpty());
    }

    /**
     * True when the service name is the only condition, which the per-service catalogs answer exactly and in full;
     * a sample is only worth taking for anything narrower.
     */
    protected static boolean onlyServiceName(final TraceQLQueryParams params) {
        return isSet(params.getServiceName())
            && !isSet(params.getRemoteServiceName()) && !isSet(params.getServiceInstance()) && !isSet(params.getSpanName())
            && params.getMinDuration() == null && params.getMaxDuration() == null
            && StringUtil.isBlank(params.getStatus()) && StringUtil.isBlank(params.getKind())
            && StringUtil.isBlank(params.getHttpStatusCode())
            && (params.getTags() == null || params.getTags().isEmpty());
    }

    private static boolean isSet(final String value) {
        return StringUtil.isNotBlank(value) && !ALL.equals(value);
    }

    /**
     * The distinct non-empty values of one span property over a sample, in first-seen order.
     */
    protected static <S> List<String> distinct(final Collection<S> spans, final Function<S, String> value) {
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final S span : spans) {
            final String rendered = value.apply(span);
            if (StringUtil.isNotEmpty(rendered)) {
                values.add(rendered);
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * Tempo's deprecated {@code tags} search parameter, logfmt {@code key=value} pairs separated by spaces, as the
     * same {@link TraceQLQueryParams} a TraceQL query produces, so every datasource applies its own mapping and
     * refusals to both forms. {@code service.name} and {@code span.name} (or {@code name}) name the service and the
     * span; every other key, with a {@code span.} or {@code resource.} scope stripped, is an attribute equality.
     *
     * @throws IllegalArgumentException for an entry that is not {@code key=value}
     */
    protected static TraceQLQueryParams parseTagsParameter(final String tags) {
        final TraceQLQueryParams params = new TraceQLQueryParams();
        for (final String pair : splitLogfmt(tags)) {
            final int equals = pair.indexOf('=');
            if (equals <= 0 || equals == pair.length() - 1) {
                throw new IllegalArgumentException("Invalid tags parameter: expected key=value pairs, got " + pair);
            }
            final String key = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (SERVICE_NAME.equals(key) || RESOURCE_SERVICE_NAME.equals(key)) {
                params.setServiceName(value);
            } else if (SPAN_NAME.equals(key) || NAME.equals(key)) {
                params.setSpanName(value);
            } else {
                // kept as written, scope included, the way the TraceQL visitor stores attributes
                params.getTags().put(key, value);
            }
        }
        return params;
    }

    /**
     * logfmt pairs are separated by whitespace, except inside a double-quoted value ({@code name="GET /"}).
     */
    private static List<String> splitLogfmt(final String tags) {
        final List<String> pairs = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (final char c : tags.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    pairs.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            pairs.add(current.toString());
        }
        return pairs;
    }

    /**
     * {@code status} and {@code kind} are enums: their value lists come from the datasource's fixed set whatever
     * {@code q} says, because the dropdown is where a user goes to look for failures and a 50-trace sample seeing no
     * error is no reason to hide the value. Only attributes and span names are narrowed by the sample.
     */
    protected static boolean isEnumIntrinsic(final String tag) {
        return STATUS.equals(tag) || KIND.equals(tag);
    }

    /**
     * The spellings Tempo accepts on the tag-value endpoint, folded onto the ones the handlers switch on:
     * {@code span:status} is the {@code status} intrinsic, and an unscoped {@code .key} looks in both scopes, so it
     * becomes {@code resource.key} for the keys the resource scope lists and {@code span.key} for everything else.
     */
    protected static String normalizeTagName(final String tagName) {
        if (tagName.startsWith(SPAN_INTRINSIC_PREFIX)) {
            return tagName.substring(SPAN_INTRINSIC_PREFIX.length());
        }
        if (tagName.startsWith(".") && tagName.length() > 1) {
            final String key = tagName.substring(1);
            return (RESOURCE_TAG_KEYS.contains(key) ? SCOPE_RESOURCE : SCOPE_SPAN) + "." + key;
        }
        return tagName;
    }

    /**
     * Tempo's {@code spss}: the spans a span set lists, default 3; zero or a negative value means every match.
     */
    protected static int spansPerSpanSet(final Optional<Integer> spss) {
        return spss.filter(value -> value > 0).orElse(DEFAULT_SPANS_PER_SPAN_SET);
    }

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

