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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.GenAITraceScopeCondition;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBGenAIEvaluationRecordQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads GenAI evaluation records the way the query service does, by each criterion on a tag with a skipping index —
 * the value type, the evaluation level and the trace — on the real {@code gen_ai_evaluation_record} stream. The
 * matches must come back wherever they sit in their series, not only within the first {@code limit + offset} records
 * of it (apache/skywalking#14215, fixed in BanyanDB by apache/skywalking-banyandb#1476).
 */
@Testcontainers
public class BanyanDBGenAIEvaluationRecordQueryIT {
    /** Records in the one series, one second apart. */
    private static final int RECORDS = 30;
    /** What the first and the last record carry, with every other record of the series between them. */
    private static final String SPLIT_TRACE = "trace-split";
    private static final GenAIEvaluationValueType SPLIT_VALUE_TYPE = GenAIEvaluationValueType.BOOLEAN;
    private static final String SPLIT_LEVEL = "TRACE";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBGenAIEvaluationRecordQueryDAO QUERY_DAO;
    private static Duration DURATION;

    /** The criteria the query service can send on a tag with a skipping index. */
    enum Criterion {
        VALUE_TYPE, EVALUATION_LEVEL, TRACE
    }

    @BeforeAll
    public static void writeRecords() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(
            GenAIEvaluationRecord.class, DefaultScopeDefine.GEN_AI_EVALUATION_RECORD, GenAIEvaluationRecord.INDEX_NAME);
        final long first = System.currentTimeMillis() - RECORDS * 1000L;
        final List<GenAIEvaluationRecord> records = new ArrayList<>();
        for (int i = 0; i < RECORDS; i++) {
            records.add(evaluation(i, first + i * 1000L));
        }
        SUPPORT.write(model, new GenAIEvaluationRecord.Builder(), records);

        QUERY_DAO = new BanyanDBGenAIEvaluationRecordQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(first, first + (RECORDS - 1) * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(RECORDS, read(null, Order.ASC, 0, RECORDS * 2).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Criterion.class)
    public void findsBothEndsInOnePage(Criterion criterion) throws IOException {
        assertEquals(Arrays.asList(id(0), id(RECORDS - 1)), read(criterion, Order.ASC, 0, 2));
        assertEquals(Arrays.asList(id(RECORDS - 1), id(0)), read(criterion, Order.DES, 0, 2));
    }

    @ParameterizedTest
    @EnumSource(Criterion.class)
    public void pagesThroughTheMatches(Criterion criterion) throws IOException {
        assertEquals(Collections.singletonList(id(0)), read(criterion, Order.ASC, 0, 1));
        assertEquals(Collections.singletonList(id(RECORDS - 1)), read(criterion, Order.ASC, 1, 1));
        assertEquals(Collections.emptyList(), read(criterion, Order.ASC, 2, 1));
    }

    private static List<String> read(Criterion criterion, Order order, int from, int limit) throws IOException {
        GenAITraceScopeCondition relatedTrace = null;
        if (criterion == Criterion.TRACE) {
            relatedTrace = new GenAITraceScopeCondition();
            relatedTrace.setTraceId(SPLIT_TRACE);
        }
        return QUERY_DAO.queryGenAIEvaluationRecord(
                            null, null, null,
                            criterion == Criterion.VALUE_TYPE ? SPLIT_VALUE_TYPE : null,
                            null, null, null, null, null,
                            criterion == Criterion.EVALUATION_LEVEL ? SPLIT_LEVEL : null,
                            null, relatedTrace, order, from, limit, DURATION
                        )
                        .getGenAIEvaluationRecordList().stream()
                        .map(GenAIEvaluationRecord::getUniqueId)
                        .collect(Collectors.toList());
    }

    private static GenAIEvaluationRecord evaluation(int index, long evaluationTime) {
        final boolean split = index == 0 || index == RECORDS - 1;
        final GenAIEvaluationRecord evaluation = new GenAIEvaluationRecord();
        evaluation.setUniqueId(id(index));
        evaluation.setTraceId(split ? SPLIT_TRACE : "trace-" + index);
        evaluation.setServiceId("service-it");
        evaluation.setProviderId("provider-it");
        evaluation.setModelId("model-it");
        evaluation.setOperationName("chat");
        evaluation.setValueType(split ? SPLIT_VALUE_TYPE.name() : GenAIEvaluationValueType.SCORE.name());
        evaluation.setEvalNumberValue(split ? GenAIEvaluationRecord.SCORE_SCALE : index * 1000L);
        evaluation.setEvaluationLevel(split ? SPLIT_LEVEL : "SPAN");
        evaluation.setTaskName("relevance");
        evaluation.setJudgeModel("judge-it");
        evaluation.setEvaluationTime(evaluationTime);
        evaluation.setTimeBucket(TimeBucket.getRecordTimeBucket(evaluationTime));
        return evaluation;
    }

    private static String id(int index) {
        return "evaluation-" + index;
    }
}
