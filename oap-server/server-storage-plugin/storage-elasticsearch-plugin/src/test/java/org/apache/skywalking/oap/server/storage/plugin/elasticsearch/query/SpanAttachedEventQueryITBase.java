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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin.ZipkinQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin.ZipkinSpanRecords;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import zipkin2.storage.QueryRequest;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AttachedEventFixture.DURATION;
import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AttachedEventFixture.MIDNIGHT;
import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AttachedEventFixture.stored;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads span attached events the way the trace and the Zipkin queries do on Elasticsearch and OpenSearch: the V1
 * branch reads the spans from the trace DAOs and the events from {@link SpanAttachedEventEsDAO}, and the query
 * services attach each event to the span it belongs to. A nested class of a server's IT names the server and the
 * storage mode.
 *
 * <p>The events are rover's network profiling, see {@link NativeTraceFixture} and {@link ZipkinTraceFixture}. Every
 * trace has more events than a page of the events read, events of traces not asked for name the same spans, events
 * stored with the other trace type name the same traces, and some events lie on and just outside the queries'
 * duration, which a by-id read on Elasticsearch does not bound, as it does not bound the read of the trace's spans.
 * The callers sort the events by start time themselves; the DAO's order, start second then nanos, is what they keep
 * for events starting at the same instant.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SpanAttachedEventQueryITBase {
    /** Fewer than any trace's events, so reading a trace's events pages, as {@code profileDataQueryBatchSize}. */
    private static final int EVENTS_PER_PAGE = 3;
    private static final SWSpanAttachedEventRecord.Builder SKYWALKING_EVENT = new SWSpanAttachedEventRecord.Builder();
    private static final SpanAttachedEventRecord.Builder ZIPKIN_EVENT = new SpanAttachedEventRecord.Builder();

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private SpanAttachedEventEsDAO eventDAO;
    private TraceQueryEsDAO traceDAO;
    private ZipkinQueryEsDAO zipkinDAO;
    private TraceQueryService traceQueryService;
    private ZipkinQueryService zipkinQueryService;
    private final List<SWSpanAttachedEventRecord> skyWalkingEvents = new ArrayList<>();
    private final List<SpanAttachedEventRecord> zipkinEvents = new ArrayList<>();

    SpanAttachedEventQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeTraces() throws Exception {
        support = ElasticSearchQueryITSupport.connect(
            server, mode, getClass(), config -> config.setProfileDataQueryBatchSize(EVENTS_PER_PAGE));
        final Model segments = support.installRecord(SegmentRecord.class);
        final Model zipkinSpans = support.installRecord(ZipkinSpanRecord.class);
        final Model skyWalkingEventModel = support.installRecord(SWSpanAttachedEventRecord.class);
        final Model zipkinEventModel = support.installRecord(SpanAttachedEventRecord.class);

        support.writeRecords(segments, new SegmentRecord.Builder(), NativeTraceFixture.SEGMENTS.stream()
            .map(NativeTraceFixture::record).collect(Collectors.toList()));
        support.writeRecords(zipkinSpans, new ZipkinSpanRecord.Builder(), ZipkinTraceFixture.SPANS.stream()
            .map(ZipkinSpanRecords::of).collect(Collectors.toList()));
        final List<SpanAttachedEvent> events = new ArrayList<>(NativeTraceFixture.EVENTS);
        events.addAll(ZipkinTraceFixture.EVENTS);
        for (final SpanAttachedEvent event : events) {
            if (event.getTraceContext().getType() == SpanAttachedEvent.SpanReferenceType.SKYWALKING) {
                skyWalkingEvents.add(AttachedEventFixture.skyWalkingRecord(event));
            } else {
                zipkinEvents.add(AttachedEventFixture.zipkinRecord(event));
            }
        }
        support.writeRecords(skyWalkingEventModel, SKYWALKING_EVENT, skyWalkingEvents);
        support.writeRecords(zipkinEventModel, ZIPKIN_EVENT, zipkinEvents);

        final ElasticSearchClient client = support.getClient();
        eventDAO = new SpanAttachedEventEsDAO(client, support.getConfig());
        traceDAO = new TraceQueryEsDAO(client, support.getConfig().getSegmentQueryMaxSize());
        zipkinDAO = new ZipkinQueryEsDAO(client);
        final ModuleManager moduleManager = moduleManager();
        traceQueryService = new TraceQueryService(moduleManager);
        zipkinQueryService = new ZipkinQueryService(moduleManager);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheSkyWalkingEventsOfTheListedTracesInStartOrder() throws IOException {
        assertEquals(
            stored(SKYWALKING_EVENT, skyWalkingRecords(NativeTraceFixture.EVENTS_OF_BOTH_TRACES)),
            stored(SKYWALKING_EVENT, eventDAO.querySWSpanAttachedEvents(
                Arrays.asList(NativeTraceFixture.TRACE, NativeTraceFixture.ONE_SEGMENT_TRACE), DURATION)));
        assertEquals(
            stored(SKYWALKING_EVENT, skyWalkingRecords(NativeTraceFixture.EVENTS_OF_THE_TRACE)),
            stored(SKYWALKING_EVENT, eventDAO.querySWSpanAttachedEvents(
                Collections.singletonList(NativeTraceFixture.TRACE), null)));
    }

    @Test
    public void readsTheZipkinEventsOfTheListedTracesInStartOrder() throws IOException {
        assertEquals(
            stored(ZIPKIN_EVENT, zipkinRecords(ZipkinTraceFixture.EVENTS_OF_BOTH_TRACES)),
            stored(ZIPKIN_EVENT, eventDAO.queryZKSpanAttachedEvents(
                Arrays.asList(ZipkinTraceFixture.TRACE, ZipkinTraceFixture.ONE_SERVICE_TRACE), DURATION)));
    }

    /**
     * Two events with the same name and start instant, as each pair below, are kept apart only by their trace and
     * span. Rover reports a Zipkin trace context without a segment id.
     */
    @Test
    public void keepsTheZipkinEventsOfTwoTracesOnTheSameSpanIdAtTheSameInstant() throws IOException {
        assertEquals(
            stored(ZIPKIN_EVENT, zipkinRecords(Arrays.asList(
                ZipkinTraceFixture.SAME_SPAN_ID_REQUEST, ZipkinTraceFixture.OTHER_SAME_SPAN_ID_REQUEST))),
            byTraceAndSpan(stored(ZIPKIN_EVENT, eventDAO.queryZKSpanAttachedEvents(Arrays.asList(
                ZipkinTraceFixture.SAME_SPAN_ID_TRACE, ZipkinTraceFixture.OTHER_SAME_SPAN_ID_TRACE), null))));
    }

    @Test
    public void keepsTheZipkinEventsOfTwoSpansOfATraceAtTheSameInstant() throws IOException {
        assertEquals(
            stored(ZIPKIN_EVENT, zipkinRecords(Arrays.asList(
                ZipkinTraceFixture.FIRST_SPAN_REQUEST, ZipkinTraceFixture.SECOND_SPAN_REQUEST))),
            byTraceAndSpan(stored(ZIPKIN_EVENT, eventDAO.queryZKSpanAttachedEvents(
                Collections.singletonList(ZipkinTraceFixture.TWO_SPANS_TRACE), null))));
    }

    /**
     * Two native events of different traces cannot share a segment: rover takes the trace id and the segment id
     * from the same {@code sw8} header, written by the segment's own agent.
     */
    @Test
    public void keepsTheSkyWalkingEventsOfTwoSpansOfASegmentAtTheSameInstant() throws IOException {
        assertEquals(
            stored(SKYWALKING_EVENT, skyWalkingRecords(Arrays.asList(
                NativeTraceFixture.FIRST_SPAN_SEND, NativeTraceFixture.SECOND_SPAN_SEND))),
            byTraceAndSpan(stored(SKYWALKING_EVENT, eventDAO.querySWSpanAttachedEvents(
                Collections.singletonList(NativeTraceFixture.TWO_SPANS_TRACE), null))));
    }

    @Test
    public void readsNoEventsOfATraceWithoutEvents() throws IOException {
        assertEquals(Collections.emptyList(), eventDAO.querySWSpanAttachedEvents(
            Collections.singletonList(NativeTraceFixture.UNKNOWN_TRACE), null));
        assertEquals(Collections.emptyList(), eventDAO.queryZKSpanAttachedEvents(
            Collections.singletonList(ZipkinTraceFixture.UNKNOWN_TRACE), null));
    }

    @Test
    public void attachesEachEventToItsSpanOfANativeTrace() throws IOException {
        assertEquals(NativeTraceFixture.expectedSpans(NativeTraceFixture.TRACE),
                     NativeTraceFixture.render(traceQueryService.queryTrace(NativeTraceFixture.TRACE, DURATION)));
        assertEquals(NativeTraceFixture.expectedSpans(NativeTraceFixture.ONE_SEGMENT_TRACE),
                     NativeTraceFixture.render(
                         traceQueryService.queryTrace(NativeTraceFixture.ONE_SEGMENT_TRACE, null)));
    }

    @Test
    public void attachesEachEventToItsSpanOfAZipkinTraceReadById() throws IOException {
        assertEquals(ZipkinTraceFixture.expectedTraces(ZipkinTraceFixture.TRACE),
                     ZipkinTraceFixture.canonical(Collections.singletonList(
                         zipkinQueryService.getTraceById(ZipkinTraceFixture.TRACE, DURATION))));
        assertEquals(ZipkinTraceFixture.expectedTraces(ZipkinTraceFixture.ONE_SERVICE_TRACE),
                     ZipkinTraceFixture.canonical(Collections.singletonList(
                         zipkinQueryService.getTraceById(ZipkinTraceFixture.ONE_SERVICE_TRACE, null))));
    }

    @Test
    public void attachesEachEventToItsSpanOfTheZipkinTracesSearched() throws IOException {
        final QueryRequest request = QueryRequest.newBuilder()
                                                 .endTs(MIDNIGHT + 59_000L)
                                                 .lookback(119_000L)
                                                 .limit(2)
                                                 .build();
        assertEquals(ZipkinTraceFixture.expectedTraces(ZipkinTraceFixture.TRACE, ZipkinTraceFixture.ONE_SERVICE_TRACE),
                     ZipkinTraceFixture.canonical(zipkinQueryService.getTraces(request, DURATION)));
    }

    @Test
    public void attachesEachEventToItsSpanOfTheZipkinTracesReadByIds() throws IOException {
        final Set<String> ids = new LinkedHashSet<>(Arrays.asList(
            ZipkinTraceFixture.TRACE, ZipkinTraceFixture.ONE_SERVICE_TRACE, ZipkinTraceFixture.UNKNOWN_TRACE));
        assertEquals(ZipkinTraceFixture.expectedTraces(ZipkinTraceFixture.TRACE, ZipkinTraceFixture.ONE_SERVICE_TRACE),
                     ZipkinTraceFixture.canonical(zipkinQueryService.getTracesByIds(ids, null)));
    }

    /**
     * Hands the query services this plugin's DAOs, as the storage module would.
     */
    private ModuleManager moduleManager() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder storage = services(moduleManager, StorageModule.NAME);
        when(storage.getService(ITraceQueryDAO.class)).thenReturn(traceDAO);
        when(storage.getService(IZipkinQueryDAO.class)).thenReturn(zipkinDAO);
        when(storage.getService(ISpanAttachedEventQueryDAO.class)).thenReturn(eventDAO);
        final IComponentLibraryCatalogService components = mock(IComponentLibraryCatalogService.class);
        when(components.getComponentName(anyInt())).thenAnswer(
            invocation -> NativeTraceFixture.COMPONENTS.get(invocation.<Integer>getArgument(0)));
        final ModuleServiceHolder core = services(moduleManager, CoreModule.NAME);
        when(core.getService(IComponentLibraryCatalogService.class)).thenReturn(components);
        return moduleManager;
    }

    private static ModuleServiceHolder services(final ModuleManager moduleManager, final String moduleName) {
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(moduleName)).thenReturn(provider);
        when(provider.provider()).thenReturn(services);
        return services;
    }

    private static List<SWSpanAttachedEventRecord> skyWalkingRecords(final List<SpanAttachedEvent> events) {
        return events.stream().map(AttachedEventFixture::skyWalkingRecord).collect(Collectors.toList());
    }

    private static List<SpanAttachedEventRecord> zipkinRecords(final List<SpanAttachedEvent> events) {
        return events.stream().map(AttachedEventFixture::zipkinRecord).collect(Collectors.toList());
    }

    /** Events starting at the same instant come in no order. */
    private static List<Map<String, Object>> byTraceAndSpan(final List<Map<String, Object>> events) {
        events.sort(Comparator.comparing((Map<String, Object> columns) ->
                                             (String) columns.get(SpanAttachedEventRecord.RELATED_TRACE_ID))
                              .thenComparing(columns -> (String) columns.get(SpanAttachedEventRecord.TRACE_SPAN_ID)));
        return events;
    }
}
