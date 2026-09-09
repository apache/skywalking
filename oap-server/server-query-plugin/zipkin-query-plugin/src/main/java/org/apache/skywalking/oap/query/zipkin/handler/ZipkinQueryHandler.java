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

package org.apache.skywalking.oap.query.zipkin.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.gson.Gson;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.ResponseHeadersBuilder;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.DateTime;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.QueryRequest;

import static com.linecorp.armeria.common.HttpHeaderNames.CACHE_CONTROL;
import static com.linecorp.armeria.common.HttpStatus.BAD_REQUEST;
import static com.linecorp.armeria.common.HttpStatus.NOT_FOUND;
import static com.linecorp.armeria.common.MediaType.ANY_TEXT_TYPE;

/**
 * Reference from zipkin2.server.internal.ZipkinQueryApiV2 for the API consistent.
 */
@ExceptionHandler(ZipkinQueryExceptionHandler.class)
public class ZipkinQueryHandler {
    private final ZipkinQueryConfig config;
    private final ModuleManager moduleManager;
    private final ZipkinQueryService zipkinQueryService;
    private TagAutoCompleteQueryService tagQueryService;
    private final long defaultLookback;
    private final int namesMaxAge;
    private static final Gson GSON = new Gson();
    private boolean supportTraceV2 = false;

    volatile int serviceCount;

    public ZipkinQueryHandler(final ZipkinQueryConfig config,
                              ModuleManager moduleManager) {
        this.config = config;
        this.moduleManager = moduleManager;
        this.defaultLookback = config.getLookback();
        this.namesMaxAge = config.getNamesMaxAge();
        this.zipkinQueryService = new ZipkinQueryService(moduleManager);
    }

    private TagAutoCompleteQueryService getTagQueryService() {
        if (tagQueryService == null) {
            this.tagQueryService = moduleManager.find(CoreModule.NAME).provider().getService(TagAutoCompleteQueryService.class);
        }
        return tagQueryService;
    }

    @Get("/config.json")
    @Blocking
    public AggregatedHttpResponse getUIConfig() throws IOException {
        StringWriter writer = new StringWriter();
        JsonGenerator generator = new JsonFactory().createGenerator(writer);
        generator.writeStartObject();
        generator.writeStringField("environment", config.getUiEnvironment());
        generator.writeNumberField("queryLimit", config.getUiQueryLimit());
        generator.writeNumberField("defaultLookback", config.getUiDefaultLookback());
        generator.writeBooleanField("searchEnabled", config.isUiSearchEnabled());
        generator.writeObjectFieldStart("dependency");
        generator.writeBooleanField("enabled", false); //not provide zipkin dependency diagram
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();
        return AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON, HttpData.ofUtf8(writer.toString()));
    }

    @Get("/api/v2/services")
    @Blocking
    public AggregatedHttpResponse getServiceNames() throws IOException {
        List<String> serviceNames = zipkinQueryService.getServiceNames();
        serviceCount = serviceNames.size();
       return cachedResponse(serviceCount > 3, serviceNames);
    }

    @Get("/api/v2/remoteServices")
    @Blocking
    public AggregatedHttpResponse getRemoteServiceNames(@Param("serviceName") String serviceName) throws IOException {
        List<String> remoteServiceNames = zipkinQueryService.getRemoteServiceNames(serviceName);
        return cachedResponse(serviceCount > 3, remoteServiceNames);
    }

    @Get("/api/v2/spans")
    @Blocking
    public AggregatedHttpResponse getSpanNames(@Param("serviceName") String serviceName) throws IOException {
        List<String> spanNames = zipkinQueryService.getSpanNames(serviceName);
        return cachedResponse(serviceCount > 3, spanNames);
    }

    /**
     * {@code coldStage}, {@code endTs} and {@code lookback} are SkyWalking additions to the Zipkin API, see
     * {@link #buildOptionalDuration(Optional, Optional, Optional)}.
     */
    @Get("/api/v2/trace/{traceId}")
    @Blocking
    public AggregatedHttpResponse getTraceById(@Param("traceId") String traceId,
                                               @Param("coldStage") Optional<Boolean> coldStage,
                                               @Param("endTs") Optional<Long> endTs,
                                               @Param("lookback") Optional<Long> lookback) throws IOException {
        final Duration duration = buildOptionalDuration(coldStage, endTs, lookback);
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                builder.append("Condition: traceId: ")
                       .append(traceId)
                       .append(", duration: ")
                       .append(duration);
                debuggingSpan = traceContext.createSpan("Query /api/v2/trace/{traceId}");
                debuggingSpan.setMsg(builder.toString());
            }
            if (StringUtil.isEmpty(traceId)) {
                return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceId is empty or null");
            }
            List<Span> trace = zipkinQueryService.getTraceById(traceId, duration);
            if (CollectionUtils.isEmpty(trace)) {
                return AggregatedHttpResponse.of(NOT_FOUND, ANY_TEXT_TYPE, traceId + " not found");
            }
            return response(SpanBytesEncoder.JSON_V2.encodeList(trace));
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);
            }
        }
    }

    @Get("/api/v2/traces")
    @Blocking
    public AggregatedHttpResponse getTraces(
        @Param("serviceName") Optional<String> serviceName,
        @Param("remoteServiceName") Optional<String> remoteServiceName,
        @Param("spanName") Optional<String> spanName,
        @Param("annotationQuery") Optional<String> annotationQuery,
        @Param("minDuration") Optional<Long> minDuration,
        @Param("maxDuration") Optional<Long> maxDuration,
        @Param("endTs") Optional<Long> endTs,
        @Param("lookback") Optional<Long> lookback,
        @Default("10") @Param("limit") int limit,
        @Param("coldStage") Optional<Boolean> coldStage) throws IOException {
        QueryRequest queryRequest =
            QueryRequest.newBuilder()
                        .serviceName(serviceName.orElse(null))
                        .remoteServiceName(remoteServiceName.orElse(null))
                        .spanName(spanName.orElse(null))
                        .parseAnnotationQuery(annotationQuery.orElse(null))
                        .minDuration(minDuration.orElse(null))
                        .maxDuration(maxDuration.orElse(null))
                        .endTs(endTs.orElse(System.currentTimeMillis()))
                        .lookback(lookback.orElse(defaultLookback))
                        .limit(limit)
                        .build();
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                builder.append("Condition: QueryRequest: ")
                       .append(queryRequest)
                       .append(", coldStage: ")
                       .append(coldStage.orElse(false));
                debuggingSpan = traceContext.createSpan("Query /api/v2/traces");
                debuggingSpan.setMsg(builder.toString());
            }
            final Duration duration = buildDuration(
                queryRequest.endTs(), queryRequest.lookback(), coldStage.orElse(false));
            List<List<Span>> traces = zipkinQueryService.getTraces(queryRequest, duration);
            return response(encodeTraces(traces));
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);
            }
        }
    }

    /**
     * {@code coldStage}, {@code endTs} and {@code lookback} are SkyWalking additions to the Zipkin API, see
     * {@link #buildOptionalDuration(Optional, Optional, Optional)}.
     */
    @Get("/api/v2/traceMany")
    @Blocking
    public AggregatedHttpResponse getTracesByIds(@Param("traceIds") String traceIds,
                                                 @Param("coldStage") Optional<Boolean> coldStage,
                                                 @Param("endTs") Optional<Long> endTs,
                                                 @Param("lookback") Optional<Long> lookback) throws IOException {
        if (StringUtil.isEmpty(traceIds)) {
            return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceIds is empty or null");
        }

        Set<String> normalizeTraceIds = new LinkedHashSet<>();
        String[] traceIdsArr = traceIds.split(",", 1000);
        for (String traceId : traceIdsArr) {
            if (!normalizeTraceIds.add(Span.normalizeTraceId(traceId.trim()))) {
                return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceId: " + traceId + " duplicate ");
            }
        }
        List<List<Span>> traces = zipkinQueryService.getTracesByIds(
            normalizeTraceIds, buildOptionalDuration(coldStage, endTs, lookback));
        if (CollectionUtils.isEmpty(traces)) {
            return AggregatedHttpResponse.of(NOT_FOUND, ANY_TEXT_TYPE, traceIds + " not found");
        }
        return response(encodeTraces(traces));
    }

    @Get("/api/v2/autocompleteKeys")
    @Blocking
    public AggregatedHttpResponse getAutocompleteKeys() throws IOException {
        final Duration duration = buildDuration(System.currentTimeMillis(), defaultLookback, false);
        Set<String> autocompleteKeys = getTagQueryService().queryTagAutocompleteKeys(TagType.ZIPKIN, duration);
        return cachedResponse(true, new ArrayList<>(autocompleteKeys));
    }

    @Get("/api/v2/autocompleteValues")
    @Blocking
    public AggregatedHttpResponse getAutocompleteValues(@Param("key") String key) throws IOException {
        final Duration duration = buildDuration(System.currentTimeMillis(), defaultLookback, false);
        Set<String> autocompleteValues = getTagQueryService().queryTagAutocompleteValues(TagType.ZIPKIN, key, duration);
        return cachedResponse(autocompleteValues.size() > 3, new ArrayList<>(autocompleteValues));
    }

    /**
     * Build the storage query duration from the Zipkin-style {@code endTs}/{@code lookback} pair (both in
     * milliseconds). {@code coldStage} targets the BanyanDB cold lifecycle stage and is ignored by the other
     * storages, like {@code Duration.coldStage} in the GraphQL trace query.
     */
    private Duration buildDuration(final long endTs, final long lookback, final boolean coldStage) {
        final Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        final DateTime endTime = new DateTime(endTs);
        final DateTime startTime = endTime.minus(org.joda.time.Duration.millis(lookback));
        duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
        duration.setColdStage(coldStage);
        return duration;
    }

    /**
     * The Zipkin API has no time range on the trace-by-id lookups, so a plain Zipkin request keeps the
     * pre-existing null duration and the storage default (BanyanDB searches everything its hot/warm stages
     * retain). Only when the caller passes any of the additional {@code coldStage}/{@code endTs}/{@code lookback}
     * parameters is a duration built, with the same defaults as {@code /api/v2/traces}: {@code endTs} is now,
     * {@code lookback} is the configured {@code lookback}.
     */
    @Nullable
    private Duration buildOptionalDuration(final Optional<Boolean> coldStage,
                                           final Optional<Long> endTs,
                                           final Optional<Long> lookback) {
        if (coldStage.isEmpty() && endTs.isEmpty() && lookback.isEmpty()) {
            return null;
        }
        return buildDuration(
            endTs.orElse(System.currentTimeMillis()), lookback.orElse(defaultLookback), coldStage.orElse(false));
    }

    private AggregatedHttpResponse response(byte[] body) {
        return AggregatedHttpResponse.of(ResponseHeaders.builder(HttpStatus.OK)
                                                        .contentType(MediaType.JSON)
                                                        .build(), HttpData.wrap(body));
    }

    private AggregatedHttpResponse cachedResponse(boolean shouldCache, List<String> values) {
        Collections.sort(values);
        ResponseHeadersBuilder headers = ResponseHeaders.builder(HttpStatus.OK)
                                                        .contentType(MediaType.JSON);
        if (shouldCache) {
            headers = headers.add(CACHE_CONTROL, "max-age=" + namesMaxAge + ", must-revalidate");
        }
        return AggregatedHttpResponse.of(headers.build(), HttpData.ofUtf8(GSON.toJson(values)));
    }

    private byte[] encodeTraces(List<List<Span>> traces) {
        if (CollectionUtils.isEmpty(traces)) {
            return new byte[] {
                '[',
                ']'
            };
        }
        List<byte[]> encodedTraces = new ArrayList<>(traces.size());
        int tracesSize = traces.size();
        int length = 0;
        for (List<Span> trace : traces) {
            byte[] traceByte = SpanBytesEncoder.JSON_V2.encodeList(trace);
            encodedTraces.add(traceByte);
            length += traceByte.length;
        }
        //bytes length = length + '[' + ']' + join ','
        byte[] allByteArray = new byte[length + 2 + traces.size() - 1];
        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        buff.put((byte) '[');
        for (int i = 0; i < tracesSize; i++) {
            buff.put(encodedTraces.get(i));
            if (i < tracesSize - 1)
                buff.put((byte) ',');
        }
        buff.put((byte) ']');
        return buff.array();
    }
}
