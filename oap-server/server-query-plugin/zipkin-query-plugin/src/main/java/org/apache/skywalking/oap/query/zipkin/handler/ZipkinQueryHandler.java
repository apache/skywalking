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
import com.google.protobuf.InvalidProtocolBufferException;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.DateTime;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
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
    private IZipkinQueryDAO zipkinQueryDAO;
    private ISpanAttachedEventQueryDAO spanAttachedEventQueryDAO;
    private TagAutoCompleteQueryService tagQueryService;
    private final long defaultLookback;
    private final int namesMaxAge;
    private static final Gson GSON = new Gson();

    volatile int serviceCount;

    public ZipkinQueryHandler(final ZipkinQueryConfig config,
                              ModuleManager moduleManager) {
        this.config = config;
        this.moduleManager = moduleManager;
        this.defaultLookback = config.getLookback();
        this.namesMaxAge = config.getNamesMaxAge();
    }

    private IZipkinQueryDAO getZipkinQueryDAO() {
        if (zipkinQueryDAO == null) {
            zipkinQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IZipkinQueryDAO.class);
        }
        return zipkinQueryDAO;
    }

    private TagAutoCompleteQueryService getTagQueryService() {
        if (tagQueryService == null) {
            this.tagQueryService = moduleManager.find(CoreModule.NAME).provider().getService(TagAutoCompleteQueryService.class);
        }
        return tagQueryService;
    }

    private ISpanAttachedEventQueryDAO getSpanAttachedEventQueryDAO() {
        if (spanAttachedEventQueryDAO == null) {
            this.spanAttachedEventQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ISpanAttachedEventQueryDAO.class);
        }
        return spanAttachedEventQueryDAO;
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
        List<String> serviceNames = getZipkinQueryDAO().getServiceNames();
        serviceCount = serviceNames.size();
       return cachedResponse(serviceCount > 3, serviceNames);
    }

    @Get("/api/v2/remoteServices")
    @Blocking
    public AggregatedHttpResponse getRemoteServiceNames(@Param("serviceName") String serviceName) throws IOException {
        List<String> remoteServiceNames = getZipkinQueryDAO().getRemoteServiceNames(serviceName);
        return cachedResponse(serviceCount > 3, remoteServiceNames);
    }

    @Get("/api/v2/spans")
    @Blocking
    public AggregatedHttpResponse getSpanNames(@Param("serviceName") String serviceName) throws IOException {
        List<String> spanNames = getZipkinQueryDAO().getSpanNames(serviceName);
        return cachedResponse(serviceCount > 3, spanNames);
    }

    @Get("/api/v2/trace/{traceId}")
    @Blocking
    public AggregatedHttpResponse getTraceById(@Param("traceId") String traceId) throws IOException {
        if (StringUtil.isEmpty(traceId)) {
            return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceId is empty or null");
        }
        List<Span> trace = getZipkinQueryDAO().getTrace(Span.normalizeTraceId(traceId.trim()));
        if (CollectionUtils.isEmpty(trace)) {
            return AggregatedHttpResponse.of(NOT_FOUND, ANY_TEXT_TYPE, traceId + " not found");
        }
        appendEvents(trace, getSpanAttachedEventQueryDAO().querySpanAttachedEvents(SpanAttachedEventTraceType.ZIPKIN, traceId));
        return response(SpanBytesEncoder.JSON_V2.encodeList(trace));
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
        @Default("10") @Param("limit") int limit) throws IOException {
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
        Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        DateTime endTime = new DateTime(queryRequest.endTs());
        DateTime startTime = endTime.minus(org.joda.time.Duration.millis(queryRequest.lookback()));
        duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
        List<List<Span>> traces = getZipkinQueryDAO().getTraces(queryRequest, duration);
        return response(encodeTraces(traces));
    }

    @Get("/api/v2/traceMany")
    @Blocking
    public AggregatedHttpResponse getTracesByIds(@Param("traceIds") String traceIds) throws IOException {
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

        List<List<Span>> traces = getZipkinQueryDAO().getTraces(normalizeTraceIds);
        return response(encodeTraces(traces));
    }

    @Get("/api/v2/autocompleteKeys")
    @Blocking
    public AggregatedHttpResponse getAutocompleteKeys() throws IOException {
        Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        DateTime endTime = DateTime.now();
        DateTime startTime = endTime.minus(org.joda.time.Duration.millis(defaultLookback));
        duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
        Set<String> autocompleteKeys = getTagQueryService().queryTagAutocompleteKeys(TagType.ZIPKIN, duration);
        return cachedResponse(true, new ArrayList<>(autocompleteKeys));
    }

    @Get("/api/v2/autocompleteValues")
    @Blocking
    public AggregatedHttpResponse getAutocompleteValues(@Param("key") String key) throws IOException {
        Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        DateTime endTime = DateTime.now();
        DateTime startTime = endTime.minus(org.joda.time.Duration.millis(defaultLookback));
        duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
        Set<String> autocompleteValues = getTagQueryService().queryTagAutocompleteValues(TagType.ZIPKIN, key, duration);
        return cachedResponse(autocompleteValues.size() > 3, new ArrayList<>(autocompleteValues));
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

    private void appendEvents(List<Span> spans, List<SpanAttachedEventRecord> events) throws InvalidProtocolBufferException {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }

        final List<Tuple2<Integer, Span>> spanWithIndex = IntStream.range(0, spans.size()).mapToObj(i -> Tuple.of(i, spans.get(i))).collect(Collectors.toList());

        // sort by start time
        events.sort((e1, e2) -> {
            final int second = Long.compare(e1.getStartTimeSecond(), e2.getStartTimeSecond());
            if (second == 0) {
                return Long.compare(e1.getStartTimeNanos(), e2.getStartTimeNanos());
            }
            return second;
        });

        final Map<String, List<SpanAttachedEventRecord>> namedEvents = events.stream()
            .collect(Collectors.groupingBy(SpanAttachedEventRecord::getEvent, Collectors.toList()));

        final Map<String, Tuple2<Span.Builder, Integer>> spanCache = new HashMap<>();
        for (Map.Entry<String, List<SpanAttachedEventRecord>> namedEntry : namedEvents.entrySet()) {
            for (int i = 1; i <= namedEntry.getValue().size(); i++) {
                final SpanAttachedEventRecord record = namedEntry.getValue().get(i - 1);
                String eventName = record.getEvent() + (namedEntry.getValue().size() == 1 ? "" : "-" + i);
                final SpanAttachedEvent event = SpanAttachedEvent.parseFrom(record.getDataBinary());

                // find matched span
                Tuple2<Span.Builder, Integer> spanBuilder = spanCache.get(record.getTraceSpanId());
                if (spanBuilder == null) {
                    Tuple2<Integer, Span> matchesSpan = spanWithIndex.stream().filter(s -> Objects.equals(s._2.id(), record.getTraceSpanId())).
                        findFirst().orElse(null);
                    if (matchesSpan == null) {
                        continue;
                    }

                    // if the event is server side, then needs to change to the upstream span
                    final String direction = getSpanAttachedEventTagValue(event.getTagsList(), "data_direction");
                    final String type = getSpanAttachedEventTagValue(event.getTagsList(), "data_type");
                    if (("request".equals(type) && "inbound".equals(direction)) || ("response".equals(type) && "outbound".equals(direction))) {
                        final String parentSpanId = matchesSpan._2.id();
                        matchesSpan = spanWithIndex.stream().filter(s -> Objects.equals(s._2.parentId(), parentSpanId)
                            && Objects.equals(s._2.kind(), Span.Kind.SERVER)).findFirst().orElse(matchesSpan);
                    }

                    spanBuilder = Tuple.of(matchesSpan._2.toBuilder(), matchesSpan._1);
                    spanCache.put(record.getTraceSpanId(), spanBuilder);
                }

                appendEvent(spanBuilder._1, eventName, event);
            }
        }

        // re-build modified spans
        for (Map.Entry<String, Tuple2<Span.Builder, Integer>> entry : spanCache.entrySet()) {
            spans.set(entry.getValue()._2, entry.getValue()._1.build());
        }
    }

    private void appendEvent(Span.Builder span, String eventName, SpanAttachedEvent event) {
        span.addAnnotation(
            TimeUnit.SECONDS.toMicros(event.getStartTime().getSeconds()) + TimeUnit.NANOSECONDS.toMicros(event.getStartTime().getNanos()),
            "Start " + eventName);
        span.addAnnotation(
            TimeUnit.SECONDS.toMicros(event.getEndTime().getSeconds()) + TimeUnit.NANOSECONDS.toMicros(event.getEndTime().getNanos()),
            "Finished " + eventName);

        final Yaml yaml = new Yaml();
        if (event.getSummaryList().size() > 0) {
            final Map<String, Long> summaries = event.getSummaryList().stream().collect(Collectors.toMap(
                KeyIntValuePair::getKey, KeyIntValuePair::getValue, (s1, s2) -> s1));
            String summary = yaml.dumpAs(summaries, Tag.MAP, DumperOptions.FlowStyle.AUTO);
            span.putTag(formatEventTagKey(eventName + ".summary"), summary);
        }
        if (event.getTagsList().size() > 0) {
            final Map<String, String> tags = event.getTagsList().stream().collect(Collectors.toMap(
                KeyStringValuePair::getKey, KeyStringValuePair::getValue, (s1, s2) -> s1));
            String summary = yaml.dumpAs(tags, Tag.MAP, DumperOptions.FlowStyle.AUTO);
            span.putTag(formatEventTagKey(eventName + ".tags"), summary);
        }
    }

    private String formatEventTagKey(String name) {
        return name.replaceAll(" ", ".").toLowerCase(Locale.ROOT);
    }

    private String getSpanAttachedEventTagValue(List<KeyStringValuePair> values, String tagKey) {
        for (KeyStringValuePair pair : values) {
            if (Objects.equals(pair.getKey(), tagKey)) {
                return pair.getValue();
            }
        }
        return null;
    }
}
