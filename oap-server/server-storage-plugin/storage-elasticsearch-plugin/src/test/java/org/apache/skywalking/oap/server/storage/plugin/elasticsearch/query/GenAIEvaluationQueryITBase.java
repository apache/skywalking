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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.GenAITraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAITraceRef;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads GenAI evaluation records the way the GenAI evaluation query service does on Elasticsearch and OpenSearch,
 * through {@link GenAIEvaluationRecordQueryEsDAO}: by service, provider, model, value type, score bounds, boolean
 * value, task, evaluation level, judge model and the trace, native segment and span index, or OTLP and Zipkin span
 * it was produced from, newest, oldest, highest or lowest score first, page by page. A nested class of a server's IT
 * names the server and the storage mode.
 *
 * <p>The records are written as the span evaluation writes them, on both sides of midnight, so a read spans two daily
 * indices. For each filter a record matches every other condition but that one, and the score bounds lie on a record's
 * score. Records of their own lie on and just outside the bounds of a duration: on its first millisecond and the one
 * before, on its last millisecond, and just after its end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class GenAIEvaluationQueryITBase {
    /** A fixed local midnight, so the daily indices the records are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final long MINUTE = 60_000L;
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);

    private static final String CHAT = "chat-app";
    private static final String OPENAI = "openai";
    private static final String TRACE_1 = "trace-1";
    private static final String TRACE_2 = "trace-2";
    private static final String OTLP_SPAN = "a1b2c3d4e5f60718";

    private static final List<Evaluation> EVALUATIONS = Arrays.asList(
        new Evaluation("g1", MIDNIGHT - 150_000L, CHAT, OPENAI, "gpt-4o", "relevance", "judge-a")
            .score(800_000L, "SPAN").nativeSpan(TRACE_1, "segment-1", 0),
        new Evaluation("g2", MIDNIGHT - 110_000L, CHAT, OPENAI, "gpt-4o", "safety", "judge-a")
            .bool(true, "TRACE").nativeSpan(TRACE_1, "segment-1", 1),
        new Evaluation("g3", MIDNIGHT - 70_000L, CHAT, OPENAI, "gpt-4o-mini", "relevance", "judge-b")
            .score(300_000L, "SPAN").nativeSpan(TRACE_1, "segment-2", 0),
        new Evaluation("g4", MIDNIGHT - 30_000L, "search-app", "anthropic", "claude", "relevance", "judge-a")
            .score(950_000L, "SPAN").span(GenAITraceRefType.OTLP, TRACE_2, OTLP_SPAN),
        new Evaluation("g5", MIDNIGHT + 30_000L, CHAT, "anthropic", "claude", "safety", "judge-b")
            .bool(false, "SPAN").span(GenAITraceRefType.ZIPKIN, "trace-3", "0000000000000001"),
        new Evaluation("g6", MIDNIGHT + 70_000L, CHAT, OPENAI, "gpt-4o", "tone", "judge-a")
            .string("polite", "SPAN").span(GenAITraceRefType.OTLP, TRACE_2, "a1b2c3d4e5f60719"),
        new Evaluation("g7", MIDNIGHT + 110_000L, CHAT, OPENAI, "gpt-4o", "relevance", "judge-a")
            .score(600_000L, "SPAN").nativeSpan("trace-4", "segment-4", 0),
        bound("before-start", BOUNDED.getStartTimestamp() - 1),
        bound("on-start", BOUNDED.getStartTimestamp()),
        bound("last-millisecond", BOUNDED.getEndTimestamp() - 1),
        bound("after-end", BOUNDED.getEndTimestamp() + 1)
    );

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private GenAIEvaluationRecordQueryEsDAO dao;

    GenAIEvaluationQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeEvaluations() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(GenAIEvaluationRecord.class);
        support.writeRecords(model, new GenAIEvaluationRecord.Builder(), EVALUATIONS.stream()
            .map(Evaluation::record).collect(Collectors.toList()));
        dao = new GenAIEvaluationRecordQueryEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheEvaluationsOfAServiceNewestOrOldestFirst() throws IOException {
        assertEquals(Arrays.asList("g7", "g6", "g5", "g3", "g2", "g1"), ids(read().service(CHAT)));
        assertEquals(Arrays.asList("g1", "g2", "g3", "g5", "g6", "g7"), ids(read().service(CHAT).order(Order.ASC)));
    }

    @Test
    public void readsTheEvaluationsOfAProviderAndOfAModel() throws IOException {
        assertEquals(Arrays.asList("g7", "g6", "g3", "g2", "g1"), ids(read().provider(OPENAI)));
        assertEquals(Arrays.asList("g7", "g6", "g2", "g1"), ids(read().model(OPENAI, "gpt-4o")));
    }

    @Test
    public void readsTheEvaluationsOfAValueType() throws IOException {
        assertEquals(Arrays.asList("g7", "g4", "g3", "g1"), ids(read().valueType(GenAIEvaluationValueType.SCORE)));
        assertEquals(Collections.singletonList("g6"), ids(read().valueType(GenAIEvaluationValueType.STRING)));
    }

    /**
     * The query service reads score bounds and the score order of scores only, and a boolean value of booleans
     * only. A bound lies on a score.
     */
    @Test
    public void readsTheScoresWithinTheBoundsAndTheBooleansOfAValue() throws IOException {
        assertEquals(Arrays.asList("g7", "g4", "g1"), ids(read().scores(600_000L, null)));
        assertEquals(Arrays.asList("g7", "g3", "g1"), ids(read().scores(null, 800_000L)));
        assertEquals(Arrays.asList("g7", "g1"), ids(read().scores(500_000L, 900_000L)));
        assertEquals(Collections.singletonList("g2"), ids(read().bool(true)));
        assertEquals(Collections.singletonList("g5"), ids(read().bool(false)));
    }

    @Test
    public void readsTheScoresHighestOrLowestFirst() throws IOException {
        assertEquals(Arrays.asList("g4", "g1", "g7", "g3"), ids(read().scores(null, null).byScore()));
        assertEquals(Arrays.asList("g3", "g7", "g1", "g4"), ids(read().scores(null, null).byScore()
                                                                       .order(Order.ASC)));
    }

    @Test
    public void readsTheEvaluationsOfATaskALevelAndAJudgeModel() throws IOException {
        assertEquals(Arrays.asList("g7", "g4", "g3", "g1"), ids(read().task("relevance")));
        assertEquals(Collections.singletonList("g2"), ids(read().level("TRACE")));
        assertEquals(Arrays.asList("g5", "g3"), ids(read().judge("judge-b")));
    }

    @Test
    public void readsTheEvaluationsOfANativeTraceSegmentAndSpan() throws IOException {
        assertEquals(Arrays.asList("g3", "g2", "g1"), ids(read().trace(GenAITraceRefType.SKYWALKING_NATIVE, TRACE_1,
                                                                       null, null, null)));
        assertEquals(Arrays.asList("g2", "g1"), ids(read().trace(GenAITraceRefType.SKYWALKING_NATIVE, TRACE_1,
                                                                 "segment-1", null, null)));
        assertEquals(Collections.singletonList("g2"), ids(read().trace(GenAITraceRefType.SKYWALKING_NATIVE, TRACE_1,
                                                                       "segment-1", 1, null)));
    }

    /**
     * The trace's store is part of the reference: a Zipkin trace is not found as an OTLP one.
     */
    @Test
    public void readsTheEvaluationsOfAnOtlpOrZipkinTraceAndSpan() throws IOException {
        assertEquals(Arrays.asList("g6", "g4"), ids(read().trace(GenAITraceRefType.OTLP, TRACE_2, null, null, null)));
        assertEquals(Collections.singletonList("g4"), ids(read().trace(GenAITraceRefType.OTLP, TRACE_2, null, null,
                                                                       OTLP_SPAN)));
        assertEquals(Collections.singletonList("g5"), ids(read().trace(GenAITraceRefType.ZIPKIN, "trace-3", null,
                                                                       null, null)));
        assertEquals(Collections.emptyList(), ids(read().trace(GenAITraceRefType.OTLP, "trace-3", null, null, null)));
    }

    @Test
    public void pagesThroughTheEvaluationsOfAService() throws IOException {
        assertEquals(Arrays.asList("g7", "g6"), ids(read().service(CHAT).page(0, 2)));
        assertEquals(Arrays.asList("g5", "g3"), ids(read().service(CHAT).page(2, 2)));
        assertEquals(Arrays.asList("g2", "g1"), ids(read().service(CHAT).page(4, 2)));
        assertEquals(Collections.emptyList(), ids(read().service(CHAT).page(6, 2)));
    }

    @Test
    public void readsEachEvaluationWhole() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            expected.add(render(EVALUATIONS.get(i).record()));
        }
        assertEquals(expected, read().run(dao).stream().map(GenAIEvaluationQueryITBase::render)
                                     .collect(Collectors.toList()));
    }

    @Test
    public void readsTheEvaluationsOfEveryDayWithoutADuration() throws IOException {
        assertEquals(Arrays.asList("g3", "g2", "g1"), ids(read().trace(GenAITraceRefType.SKYWALKING_NATIVE, TRACE_1,
                                                                       null, null, null).during(null)));
    }

    @Test
    public void keepsTheEvaluationsWithinTheDuration() throws IOException {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), ids(read().service("bounds-app").during(BOUNDED)));
    }

    private List<String> ids(final EvaluationRead read) throws IOException {
        return read.run(dao).stream().map(GenAIEvaluationRecord::getUniqueId).collect(Collectors.toList());
    }

    private static EvaluationRead read() {
        return new EvaluationRead();
    }

    private static String render(final GenAIEvaluationRecord record) {
        final GenAITraceRef ref = record.getTraceRef();
        return String.join("|", record.getUniqueId(), record.getServiceId(), record.getProviderId(),
                           record.getModelId(), record.getOperationName(), String.valueOf(record.getValueType()),
                           String.valueOf(record.getEvalNumberValue()), record.getEvalStringValue(),
                           record.getTaskName(), record.getEvaluationLevel(), record.getReason(),
                           record.getJudgeModel(), String.valueOf(record.getEvaluationTime()),
                           String.valueOf(ref.getType()), ref.getTraceId(), ref.getSegmentId(),
                           String.valueOf(ref.getSpanIndex()), ref.getSpanId());
    }

    private static Duration minutes(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmm"));
        return duration;
    }

    private static Evaluation bound(final String id, final long time) {
        return new Evaluation(id, time, "bounds-app", OPENAI, "gpt-4o", "relevance", "judge-a")
            .score(500_000L, "SPAN").nativeSpan("trace-" + id, "segment-" + id, 0);
    }

    /**
     * The arguments of one read, as the GenAI evaluation query service passes them after its checks: score bounds
     * and the score order ask for scores, a boolean value for booleans.
     */
    private static final class EvaluationRead {
        private String serviceId;
        private String providerId;
        private String modelId;
        private GenAIEvaluationValueType valueType;
        private Long minScore;
        private Long maxScore;
        private Boolean booleanValue;
        private GenAIEvaluationRecordSortBy sortBy = GenAIEvaluationRecordSortBy.EVALUATION_TIME;
        private String taskName;
        private String evaluationLevel;
        private String judgeModel;
        private GenAITraceScopeCondition trace;
        private Order order = Order.DES;
        private int from = 0;
        private int limit = 100;
        private Duration duration = AROUND_MIDNIGHT;

        EvaluationRead service(final String service) {
            serviceId = GenAIEvaluationRecord.toServiceId(service);
            return this;
        }

        EvaluationRead provider(final String provider) {
            providerId = GenAIEvaluationRecord.toEntityId(provider);
            return this;
        }

        EvaluationRead model(final String provider, final String model) {
            modelId = IDManager.ServiceInstanceID.buildId(GenAIEvaluationRecord.toEntityId(provider), model);
            return this;
        }

        EvaluationRead valueType(final GenAIEvaluationValueType valueType) {
            this.valueType = valueType;
            return this;
        }

        EvaluationRead scores(final Long min, final Long max) {
            valueType = GenAIEvaluationValueType.SCORE;
            minScore = min;
            maxScore = max;
            return this;
        }

        EvaluationRead bool(final boolean value) {
            valueType = GenAIEvaluationValueType.BOOLEAN;
            booleanValue = value;
            return this;
        }

        EvaluationRead byScore() {
            sortBy = GenAIEvaluationRecordSortBy.SCORE_VALUE;
            return this;
        }

        EvaluationRead task(final String taskName) {
            this.taskName = taskName;
            return this;
        }

        EvaluationRead level(final String evaluationLevel) {
            this.evaluationLevel = evaluationLevel;
            return this;
        }

        EvaluationRead judge(final String judgeModel) {
            this.judgeModel = judgeModel;
            return this;
        }

        EvaluationRead trace(final GenAITraceRefType type, final String traceId, final String segmentId,
                             final Integer spanIndex, final String spanId) {
            trace = new GenAITraceScopeCondition();
            trace.setType(type);
            trace.setTraceId(traceId);
            trace.setSegmentId(segmentId);
            trace.setSpanIndex(spanIndex);
            trace.setSpanId(spanId);
            return this;
        }

        EvaluationRead order(final Order order) {
            this.order = order;
            return this;
        }

        EvaluationRead page(final int from, final int limit) {
            this.from = from;
            this.limit = limit;
            return this;
        }

        EvaluationRead during(final Duration duration) {
            this.duration = duration;
            return this;
        }

        List<GenAIEvaluationRecord> run(final GenAIEvaluationRecordQueryEsDAO dao) throws IOException {
            return dao.queryGenAIEvaluationRecord(serviceId, providerId, modelId, valueType, minScore, maxScore,
                                                  booleanValue, sortBy, taskName, evaluationLevel, judgeModel, trace,
                                                  order, from, limit, duration)
                      .getGenAIEvaluationRecordList();
        }
    }

    /**
     * A GenAI evaluation as the span evaluation hands it to the record stream.
     */
    private static final class Evaluation {
        private final GenAIEvaluationRecord record;

        Evaluation(final String id, final long time, final String service, final String provider, final String model,
                   final String task, final String judge) {
            record = GenAIEvaluationRecord.create(provider, model);
            record.setUniqueId(id);
            record.setServiceName(service);
            record.setOperationName("chat");
            record.setTaskName(task);
            record.setJudgeModel(judge);
            record.setReason("reason of " + id);
            record.setEvaluationTime(time);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        }

        Evaluation score(final long scorePpm, final String level) {
            record.setValueType(GenAIEvaluationValueType.SCORE.name());
            record.setEvalNumberValue(scorePpm);
            record.setEvaluationLevel(level);
            return this;
        }

        Evaluation bool(final boolean value, final String level) {
            record.setValueType(GenAIEvaluationValueType.BOOLEAN.name());
            record.setEvalNumberValue(value ? GenAIEvaluationRecord.SCORE_SCALE : 0L);
            record.setEvaluationLevel(level);
            return this;
        }

        Evaluation string(final String value, final String level) {
            record.setValueType(GenAIEvaluationValueType.STRING.name());
            record.setEvalStringValue(value);
            record.setEvaluationLevel(level);
            return this;
        }

        Evaluation nativeSpan(final String traceId, final String segmentId, final int spanIndex) {
            record.setRefType(GenAITraceRefType.SKYWALKING_NATIVE.name());
            record.setTraceId(traceId);
            record.setSegmentId(segmentId);
            record.setSpanIndex(spanIndex);
            return this;
        }

        Evaluation span(final GenAITraceRefType type, final String traceId, final String spanId) {
            record.setRefType(type.name());
            record.setTraceId(traceId);
            record.setSpanId(spanId);
            return this;
        }

        GenAIEvaluationRecord record() {
            return record;
        }
    }
}
