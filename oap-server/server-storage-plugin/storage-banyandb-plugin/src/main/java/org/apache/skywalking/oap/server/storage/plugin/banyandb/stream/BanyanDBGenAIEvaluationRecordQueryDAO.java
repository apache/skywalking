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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.GenAITraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * Queries {@link GenAIEvaluationRecord} from BanyanDB.
 */
public class BanyanDBGenAIEvaluationRecordQueryDAO extends AbstractBanyanDBDAO implements IGenAIEvaluationRecordQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            GenAIEvaluationRecord.UNIQUE_ID,
            GenAIEvaluationRecord.TRACE_ID,
            GenAIEvaluationRecord.SERVICE_ID,
            GenAIEvaluationRecord.PROVIDER_ID,
            GenAIEvaluationRecord.MODEL_ID,
            GenAIEvaluationRecord.OPERATION_NAME,
            GenAIEvaluationRecord.EVAL_NUMBER_VALUE,
            GenAIEvaluationRecord.REF_TYPE,
            GenAIEvaluationRecord.SEGMENT_ID,
            GenAIEvaluationRecord.SPAN_INDEX,
            GenAIEvaluationRecord.SPAN_ID,
            GenAIEvaluationRecord.TASK_NAME,
            GenAIEvaluationRecord.VALUE_TYPE,
            GenAIEvaluationRecord.EVAL_STRING_VALUE,
            GenAIEvaluationRecord.EVALUATION_LEVEL,
            GenAIEvaluationRecord.REASON,
            GenAIEvaluationRecord.JUDGE_MODEL,
            GenAIEvaluationRecord.EVALUATION_TIME
    );

    public BanyanDBGenAIEvaluationRecordQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public GenAIEvaluationRecords queryGenAIEvaluationRecord(String serviceId, String providerId, String modelId, GenAIEvaluationValueType valueType, Long minScore, Long maxScore, Boolean booleanValue, GenAIEvaluationRecordSortBy sortBy,
                                                             String taskName, String evaluationLevel, String judgeModel,
                                                             GenAITraceScopeCondition relatedTrace, Order queryOrder, int from, int limit,
                                                             Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceId)) {
            where.eq(GenAIEvaluationRecord.SERVICE_ID, serviceId);
        }
        if (StringUtil.isNotEmpty(providerId)) {
            where.eq(GenAIEvaluationRecord.PROVIDER_ID, providerId);
        }
        if (StringUtil.isNotEmpty(modelId)) {
            where.eq(GenAIEvaluationRecord.MODEL_ID, modelId);
        }
        if (valueType != null) {
            where.eq(GenAIEvaluationRecord.VALUE_TYPE, valueType.name());
        }
        if (booleanValue != null) {
            where.eq(GenAIEvaluationRecord.EVAL_NUMBER_VALUE, booleanValue ? GenAIEvaluationRecord.SCORE_SCALE : 0);
        } else if (minScore != null) {
            where.gte(GenAIEvaluationRecord.EVAL_NUMBER_VALUE, minScore);
        }
        if (booleanValue == null && maxScore != null) {
            where.lte(GenAIEvaluationRecord.EVAL_NUMBER_VALUE, maxScore);
        }
        if (StringUtil.isNotEmpty(taskName)) {
            where.eq(GenAIEvaluationRecord.TASK_NAME, taskName);
        }
        if (StringUtil.isNotEmpty(evaluationLevel)) {
            where.eq(GenAIEvaluationRecord.EVALUATION_LEVEL, evaluationLevel);
        }
        if (StringUtil.isNotEmpty(judgeModel)) {
            where.eq(GenAIEvaluationRecord.JUDGE_MODEL, judgeModel);
        }

        if (Objects.nonNull(relatedTrace)) {
            if (Objects.nonNull(relatedTrace.getType())) {
                where.eq(GenAIEvaluationRecord.REF_TYPE, relatedTrace.getType().name());
            }
            if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                where.eq(GenAIEvaluationRecord.TRACE_ID, relatedTrace.getTraceId());
            }
            if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                where.eq(GenAIEvaluationRecord.SEGMENT_ID, relatedTrace.getSegmentId());
            }
            if (GenAITraceRefType.OTLP.equals(relatedTrace.getType())
                && StringUtil.isNotEmpty(relatedTrace.getSpanId())) {
                where.eq(GenAIEvaluationRecord.SPAN_ID, relatedTrace.getSpanId());
            } else if (Objects.nonNull(relatedTrace.getSpanIndex())) {
                where.eq(GenAIEvaluationRecord.SPAN_INDEX, (long) relatedTrace.getSpanIndex());
            }
        }

        if (GenAIEvaluationRecordSortBy.SCORE_VALUE.equals(sortBy)) {
            where.orderBy(GenAIEvaluationRecord.EVAL_NUMBER_VALUE, queryOrder == Order.ASC ? "ASC" : "DESC");
        } else if (queryOrder == Order.ASC) {
            where.orderByAsc();
        } else {
            where.orderByDesc();
        }
        where.limit(limit).offset(from);

        StreamQueryResponse resp = queryDebuggable(isColdStage, GenAIEvaluationRecord.INDEX_NAME, TAGS, getTimestampRange(duration), where);

        GenAIEvaluationRecords genAIEvaluationRecords = new GenAIEvaluationRecords();

        for (final RowEntity rowEntity : resp.getElements()) {
            GenAIEvaluationRecord evaluationRecord = new GenAIEvaluationRecord();
            evaluationRecord.setUniqueId(rowEntity.getTagValue(GenAIEvaluationRecord.UNIQUE_ID));
            evaluationRecord.setTraceId(rowEntity.getTagValue(GenAIEvaluationRecord.TRACE_ID));
            evaluationRecord.setServiceId(rowEntity.getTagValue(GenAIEvaluationRecord.SERVICE_ID));
            evaluationRecord.setProviderId(rowEntity.getTagValue(GenAIEvaluationRecord.PROVIDER_ID));
            evaluationRecord.setModelId(rowEntity.getTagValue(GenAIEvaluationRecord.MODEL_ID));
            evaluationRecord.setOperationName(rowEntity.getTagValue(GenAIEvaluationRecord.OPERATION_NAME));
            final Number numberValue = rowEntity.getTagValue(GenAIEvaluationRecord.EVAL_NUMBER_VALUE);
            evaluationRecord.setEvalNumberValue(numberValue == null ? null : numberValue.longValue());
            evaluationRecord.setRefType(rowEntity.getTagValue(GenAIEvaluationRecord.REF_TYPE));
            evaluationRecord.setSegmentId(rowEntity.getTagValue(GenAIEvaluationRecord.SEGMENT_ID));
            final Number spanIndex = rowEntity.getTagValue(GenAIEvaluationRecord.SPAN_INDEX);
            evaluationRecord.setSpanIndex(spanIndex == null ? null : spanIndex.intValue());
            evaluationRecord.setSpanId(rowEntity.getTagValue(GenAIEvaluationRecord.SPAN_ID));
            evaluationRecord.setTaskName(rowEntity.getTagValue(GenAIEvaluationRecord.TASK_NAME));
            final long evaluationTime =
                ((Number) rowEntity.getTagValue(GenAIEvaluationRecord.EVALUATION_TIME)).longValue();
            evaluationRecord.setEvaluationTime(evaluationTime);
            evaluationRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(evaluationTime));
            evaluationRecord.setValueType(rowEntity.getTagValue(GenAIEvaluationRecord.VALUE_TYPE));
            evaluationRecord.setEvalStringValue(rowEntity.getTagValue(GenAIEvaluationRecord.EVAL_STRING_VALUE));
            evaluationRecord.setEvaluationLevel(rowEntity.getTagValue(GenAIEvaluationRecord.EVALUATION_LEVEL));
            evaluationRecord.setReason(rowEntity.getTagValue(GenAIEvaluationRecord.REASON));
            evaluationRecord.setJudgeModel(rowEntity.getTagValue(GenAIEvaluationRecord.JUDGE_MODEL));
            genAIEvaluationRecords.getGenAIEvaluationRecordList().add(evaluationRecord);
        }
        return genAIEvaluationRecords;
    }
}
