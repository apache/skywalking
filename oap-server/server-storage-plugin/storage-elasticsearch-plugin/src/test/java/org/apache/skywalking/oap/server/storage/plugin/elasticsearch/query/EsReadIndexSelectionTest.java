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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexNames;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.otlp.OTLPTraceQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin.ZipkinQueryEsDAO;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the indices each Elasticsearch read sends, so a change to how index names are built can be checked
 * against what the reads searched before it.
 *
 * <p>Index groups count days from 2000-01-01. With a step of 3, 2020-08-07 and 2020-08-10 start a group, so
 * {@link #RANGE} (2020-08-08 to 2020-08-10) names two indices, where a step of 1 names three. Each test rolls
 * one step by 3 and the other by 1, so the names show which step the read used.
 */
@ExtendWith(MockitoExtension.class)
class EsReadIndexSelectionTest {

    private static final String METRIC = "index_selection_test_metric";

    private static final Duration RANGE = duration(Step.MINUTE, "2020-08-08 1200", "2020-08-10 1200");

    @Mock
    private ElasticSearchClient client;

    /**
     * The OAP registers every static model's table at boot, before any read, and the super dataset reads rely on
     * it to roll by the super dataset day step.
     */
    @BeforeAll
    static void registerSuperDatasetTables() {
        for (final String table : new String[] {
            "segment", "log", "gen_ai_evaluation_record", "ai_agent_session_flow", "ai_agent_session_data",
            "otlp_span", "zipkin_span"
        }) {
            IndexController.LogicIndicesRegister.registerRelation(
                new Model(
                    table, Collections.emptyList(), 0, DownSampling.Second, true, Record.class, true, false,
                    new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
                ),
                table
            );
        }
    }

    @BeforeEach
    void setUp() {
        ValueColumnMetadata.INSTANCE.putIfAbsent(METRIC, "value", Column.ValueDataType.COMMON_VALUE, 0, 1);
    }

    @AfterEach
    void tearDown() {
        ValueColumnMetadata.INSTANCE.remove(METRIC);
        IndexNames.configure(1, 1);
    }

    @Test
    void readLabeledMetricsValuesWithoutEntity_searchesTheMetricRolledByDayStep() {
        IndexNames.configure(3, 1);
        answerRangeSearch();

        // A minute step over RANGE is more points than a Duration allows, so this read takes it by the hour.
        new MetricsQueryEsDAO(client).readLabeledMetricsValuesWithoutEntity(
            METRIC, "value", Collections.emptyList(), duration(Step.HOUR, "2020-08-08 12", "2020-08-10 12"));

        assertThat(rangeSearchIndices()).containsExactly(METRIC + "-20200807", METRIC + "-20200810");
    }

    @Test
    void listEntityIdsInRange_searchesTheMetricRolledByDayStep() throws Exception {
        IndexNames.configure(3, 1);
        answerRangeSearch();

        new MetricsQueryEsDAO(client).listEntityIdsInRange(METRIC, "value", null, RANGE, 10);

        assertThat(rangeSearchIndices()).containsExactly(METRIC + "-20200807", METRIC + "-20200810");
    }

    @Test
    void listEntityIdsInRange_ofAForeignMetric_searchesTheMergedMetricsIndexRolledByDayStep() throws Exception {
        IndexNames.configure(3, 1);
        answerRangeSearch();

        new MetricsQueryEsDAO(client).listEntityIdsInRange("absent_metric", "value", "COMMON", RANGE, 10);

        assertThat(rangeSearchIndices()).containsExactly("metrics-all-20200807", "metrics-all-20200810");
    }

    @Test
    void readMetricsValues_readsEachPointFromTheIndexRolledByDayStep() throws Exception {
        IndexNames.configure(3, 1);
        answerIdsRead();

        new MetricsQueryEsDAO(client).readMetricsValues(
            serviceCondition(), "value", duration(Step.DAY, "2020-08-09", "2020-08-10"));

        assertThat(idsReadIndices()).containsExactlyInAnyOrder(METRIC + "-20200807", METRIC + "-20200810");
    }

    @Test
    void readLabeledMetricsValues_readsEachPointFromTheIndexRolledByDayStep() throws Exception {
        IndexNames.configure(3, 1);
        answerIdsRead();

        new MetricsQueryEsDAO(client).readLabeledMetricsValues(
            serviceCondition(), "value", Collections.emptyList(), duration(Step.HOUR, "2020-08-09 23", "2020-08-10 00"));

        assertThat(idsReadIndices()).containsExactlyInAnyOrder(METRIC + "-20200807", METRIC + "-20200810");
    }

    @Test
    void readHeatMap_readsEachPointFromTheIndexRolledByDayStep() {
        IndexNames.configure(3, 1);
        answerIdsRead();

        new MetricsQueryEsDAO(client).readHeatMap(
            serviceCondition(), "value", duration(Step.MINUTE, "2020-08-09 2359", "2020-08-10 0000"));

        assertThat(idsReadIndices()).containsExactlyInAnyOrder(METRIC + "-20200807", METRIC + "-20200810");
    }

    @Test
    void queryTagAutocompleteKeys_searchesTheTagIndexRolledByDayStep() throws Exception {
        IndexNames.configure(3, 1);
        answerRangeSearch();

        new TagAutoCompleteQueryDAO(client).queryTagAutocompleteKeys(TagType.TRACE, 10, RANGE);

        assertThat(rangeSearchIndices()).containsExactly("tag_autocomplete-20200807", "tag_autocomplete-20200810");
    }

    @Test
    void queryTagAutocompleteValues_withoutDuration_searchesTheWholeTagIndexFamily() throws Exception {
        IndexNames.configure(3, 1);
        answerRangeSearch();

        new TagAutoCompleteQueryDAO(client).queryTagAutocompleteValues(TagType.TRACE, "http.method", 10, null);

        assertThat(rangeSearchIndices()).containsExactly("tag_autocomplete");
    }

    @Test
    void queryBasicTraces_searchesSegmentsRolledBySuperDatasetDayStep() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new TraceQueryEsDAO(client, 100).queryBasicTraces(
            RANGE, 0, 0, null, null, null, null, 20, 0, TraceState.ALL, QueryOrder.BY_START_TIME,
            Collections.emptyList());

        assertThat(rangeSearchIndices()).containsExactly("segment-20200807", "segment-20200810");
    }

    @Test
    void queryLogs_searchesLogsRolledBySuperDatasetDayStep() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new LogQueryEsDAO(client).queryLogs(
            null, null, null, null, Order.DES, 0, 20, RANGE, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());

        assertThat(rangeSearchIndices()).containsExactly("log-20200807", "log-20200810");
    }

    @Test
    void queryGenAIEvaluationRecord_searchesRecordsRolledBySuperDatasetDayStep() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new GenAIEvaluationRecordQueryEsDAO(client).queryGenAIEvaluationRecord(
            null, null, null, null, null, null, null, null, null, null, null, null, Order.DES, 0, 20, RANGE);

        assertThat(rangeSearchIndices()).containsExactly(
            "gen_ai_evaluation_record-20200807", "gen_ai_evaluation_record-20200810");
    }

    @Test
    void queryRounds_searchesRoundsRolledBySuperDatasetDayStep() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new AIAgentConversationQueryEsDAO(client).queryRounds("service", null, null, RANGE, 10, false);

        assertThat(rangeSearchIndices()).containsExactly(
            "ai_agent_session_flow-20200807", "ai_agent_session_flow-20200810");
    }

    @Test
    void queryHeadRound_searchesTheWholeRoundIndexFamily() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new AIAgentConversationQueryEsDAO(client).queryHeadRound("service", null, "conversation", false);

        assertThat(rangeSearchIndices()).containsExactly("ai_agent_session_flow");
    }

    @Test
    void queryRoundsByNumber_searchesTheWholeRoundIndexFamily() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new AIAgentConversationQueryEsDAO(client).queryRoundsByNumber(
            "service", null, "conversation", 1, 2, 1024, false);

        assertThat(rangeSearchIndices()).containsExactly("ai_agent_session_flow");
    }

    @Test
    void queryFiles_searchesFilesRolledBySuperDatasetDayStep() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new AIAgentConversationQueryEsDAO(client).queryFiles(
            "service", null, "session", millis(2020, 8, 8, 12), millis(2020, 8, 10, 12), 0, 10, 1024, false);

        assertThat(rangeSearchIndices()).containsExactly(
            "ai_agent_session_data-20200807", "ai_agent_session_data-20200810");
    }

    @Test
    void queryFiles_withoutAStartTime_searchesTheWholeFileIndexFamily() throws Exception {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        new AIAgentConversationQueryEsDAO(client).queryFiles(
            "service", null, "session", 0, millis(2020, 8, 10, 12), 0, 10, 1024, false);

        assertThat(rangeSearchIndices()).containsExactly("ai_agent_session_data");
    }

    @Test
    void queryTraces_searchesOTLPSpansRolledBySuperDatasetDayStep() {
        IndexNames.configure(1, 3);
        answerRangeSearch();

        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setQueryDuration(RANGE);
        new OTLPTraceQueryEsDAO(client).queryTraces(condition);

        assertThat(rangeSearchIndices()).containsExactly("otlp_span-20200807", "otlp_span-20200810");
    }

    @Test
    void queryTraces_withoutDuration_searchesTheOTLPSpanAlias() {
        IndexNames.configure(1, 3);
        when(client.search(anyString(), any(Search.class))).thenReturn(new SearchResponse());

        new OTLPTraceQueryEsDAO(client).queryTraces(new OTLPTraceQueryCondition());

        verify(client).search(eq("otlp_span"), any(Search.class));
    }

    @Test
    void queryTraceById_scrollsEachOTLPSpanDayIndexRolledBySuperDatasetDayStep() {
        IndexNames.configure(1, 3);
        when(client.search(any(String[].class), any(Search.class), any(SearchParams.class)))
            .thenReturn(new SearchResponse());

        new OTLPTraceQueryEsDAO(client).queryTraceById("trace", RANGE);

        final ArgumentCaptor<String[]> indices = ArgumentCaptor.forClass(String[].class);
        verify(client).search(indices.capture(), any(Search.class), any(SearchParams.class));
        assertThat(indices.getValue()).containsExactly("otlp_span-20200807", "otlp_span-20200810");
    }

    @Test
    void queryTraceById_withoutDuration_scrollsTheOTLPSpanAlias() {
        IndexNames.configure(1, 3);
        when(client.search(anyString(), any(Search.class), any(SearchParams.class))).thenReturn(new SearchResponse());

        new OTLPTraceQueryEsDAO(client).queryTraceById("trace", null);

        verify(client).search(eq("otlp_span"), any(Search.class), any(SearchParams.class));
    }

    @Test
    void getTraces_searchesZipkinSpansRolledBySuperDatasetDayStep() {
        IndexNames.configure(1, 3);
        answerRangeSearch();
        when(client.search(anyString(), any(Search.class), any(SearchParams.class))).thenReturn(new SearchResponse());

        final QueryRequest request = QueryRequest.newBuilder()
                                                 .endTs(millis(2020, 8, 10, 12))
                                                 .lookback(2L * 24 * 3600 * 1000)
                                                 .limit(10)
                                                 .build();
        new ZipkinQueryEsDAO(client).getTraces(request, duration(Step.SECOND, "2020-08-08 120000", "2020-08-10 120000"));

        assertThat(rangeSearchIndices()).containsExactly("zipkin_span-20200807", "zipkin_span-20200810");
    }

    @Test
    void getTraces_withAStartBefore1970_searchesTheWholeZipkinSpanIndexFamily() {
        IndexNames.configure(1, 3);
        answerRangeSearch();
        when(client.search(anyString(), any(Search.class), any(SearchParams.class))).thenReturn(new SearchResponse());

        final QueryRequest request = QueryRequest.newBuilder()
                                                 .endTs(millis(2020, 8, 10, 12))
                                                 .lookback(millis(2020, 8, 10, 12))
                                                 .limit(10)
                                                 .build();
        // A lookback as long as endTs, the Zipkin API's documented default, puts the start at the epoch. A start
        // on 1969-12-31 stays before the epoch in every time zone.
        new ZipkinQueryEsDAO(client).getTraces(request, duration(Step.SECOND, "1969-12-31 000000", "2020-08-10 120000"));

        assertThat(rangeSearchIndices()).containsExactly("zipkin_span");
    }

    private void answerRangeSearch() {
        when(client.search(any(String[].class), any(Search.class))).thenReturn(new SearchResponse());
    }

    private String[] rangeSearchIndices() {
        final ArgumentCaptor<String[]> indices = ArgumentCaptor.forClass(String[].class);
        verify(client).search(indices.capture(), any(Search.class));
        return indices.getValue();
    }

    private void answerIdsRead() {
        when(client.ids(any())).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Iterable<String> idsReadIndices() {
        final ArgumentCaptor<Map<String, List<String>>> indexIdsGroup = ArgumentCaptor.forClass(Map.class);
        verify(client).ids(indexIdsGroup.capture());
        return indexIdsGroup.getValue().keySet();
    }

    private static MetricsCondition serviceCondition() {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName("service");
        entity.setNormal(true);
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(METRIC);
        condition.setEntity(entity);
        return condition;
    }

    private static Duration duration(final Step step, final String start, final String end) {
        final Duration duration = new Duration();
        duration.setStep(step);
        duration.setStart(start);
        duration.setEnd(end);
        return duration;
    }

    private static long millis(final int year, final int month, final int day, final int hour) {
        return new DateTime(year, month, day, hour, 0).getMillis();
    }
}
