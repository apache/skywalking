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

package org.apache.skywalking.oap.server.core.analysis.manual.genai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.query.type.GenAITraceRef;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Getter
@Setter
@SuperDataset
@ScopeDeclaration(id = DefaultScopeDefine.GEN_AI_EVALUATION_RECORD, name = "GenAIEvaluationRecord")
@Stream(name = GenAIEvaluationRecord.INDEX_NAME, scopeId = DefaultScopeDefine.GEN_AI_EVALUATION_RECORD,
        builder = GenAIEvaluationRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(GenAIEvaluationRecord.EVALUATION_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class GenAIEvaluationRecord extends Record {

    public static final String INDEX_NAME = "gen_ai_evaluation_record";
    public static final String UNIQUE_ID = "unique_id";
    public static final String TRACE_ID = "trace_id";
    public static final String PROVIDER_ID = "provider_id";
    public static final String MODEL_ID = "model_id";
    public static final String SERVICE_ID = "service_id";
    public static final String OPERATION_NAME = "operation_name";
    public static final String EVAL_NUMBER_VALUE = "eval_number_value";
    public static final long SCORE_SCALE = 1_000_000L;
    public static final String REF_TYPE = "ref_type";
    public static final String SEGMENT_ID = "segment_id";
    public static final String SPAN_INDEX = "span_index";
    public static final String SPAN_ID = "span_id";
    public static final String TASK_NAME = "task_name";
    public static final int TASK_NAME_MAX_LENGTH = 512;
    public static final String VALUE_TYPE = "value_type";
    public static final String EVAL_STRING_VALUE = "eval_string_value";
    public static final int EVAL_STRING_VALUE_MAX_LENGTH = 4096;
    public static final String EVALUATION_LEVEL = "evaluation_level";
    public static final String REASON = "reason";
    public static final int REASON_MAX_LENGTH = 4096;
    public static final String JUDGE_MODEL = "judge_model";
    public static final String EVALUATION_TIME = "evaluation_time";

    @Column(name = UNIQUE_ID)
    private String uniqueId;

    @Column(name = TRACE_ID, length = 150)
    @BanyanDB.IndexRule(indexType = BanyanDB.IndexRule.IndexType.SKIPPING)
    private String traceId;

    @ElasticSearch.EnableDocValues
    @Column(name = SERVICE_ID, length = 150)
    private String serviceId;

    @ElasticSearch.EnableDocValues
    @Column(name = PROVIDER_ID, length = 150)
    @BanyanDB.SeriesID(index = 0)
    @Setter(AccessLevel.PRIVATE)
    private String providerId;

    @ElasticSearch.EnableDocValues
    @Column(name = MODEL_ID, length = 150)
    @BanyanDB.SeriesID(index = 1)
    @Setter(AccessLevel.PRIVATE)
    private String modelId;

    @ElasticSearch.EnableDocValues
    @Column(name = OPERATION_NAME, length = 150)
    private String operationName;

    @ElasticSearch.EnableDocValues
    @BanyanDB.EnableSort
    @Column(name = EVAL_NUMBER_VALUE)
    private Long evalNumberValue;

    @Getter(AccessLevel.NONE)
    @Column(name = REF_TYPE, length = 32)
    private String refType;

    @Column(name = SEGMENT_ID, length = 150)
    private String segmentId;

    @Column(name = SPAN_INDEX)
    private Integer spanIndex;

    @Column(name = SPAN_ID, length = 32, storageOnly = true)
    @BanyanDB.NoIndexing
    private String spanId;

    @ElasticSearch.EnableDocValues
    @Column(name = TASK_NAME, length = TASK_NAME_MAX_LENGTH)
    private String taskName;

    @Getter(AccessLevel.NONE)
    @Column(name = VALUE_TYPE, length = 64)
    @BanyanDB.IndexRule(indexType = BanyanDB.IndexRule.IndexType.SKIPPING)
    private String valueType;

    @Column(name = EVAL_STRING_VALUE, length = EVAL_STRING_VALUE_MAX_LENGTH, storageOnly = true)
    private String evalStringValue;

    @ElasticSearch.EnableDocValues
    @Column(name = EVALUATION_LEVEL, length = 64)
    @BanyanDB.IndexRule(indexType = BanyanDB.IndexRule.IndexType.SKIPPING)
    private String evaluationLevel;

    @Column(name = REASON, length = REASON_MAX_LENGTH, storageOnly = true)
    private String reason;

    @Column(name = JUDGE_MODEL, length = 64)
    private String judgeModel;

    @ElasticSearch.EnableDocValues
    @Column(name = EVALUATION_TIME)
    private long evaluationTime;

    public Long getScoreValue() {
        return getValueType() == GenAIEvaluationValueType.SCORE ? evalNumberValue : null;
    }

    public Boolean getBooleanValue() {
        return getValueType() == GenAIEvaluationValueType.BOOLEAN && evalNumberValue != null
            ? evalNumberValue != 0 : null;
    }

    public String getStringValue() {
        final GenAIEvaluationValueType type = getValueType();
        return type == GenAIEvaluationValueType.STRING || type == GenAIEvaluationValueType.JSON
            ? evalStringValue : null;
    }

    public void setEvalStringValue(final String evalStringValue) {
        this.evalStringValue = truncate(evalStringValue, EVAL_STRING_VALUE_MAX_LENGTH);
    }

    public void setReason(final String reason) {
        this.reason = truncate(reason, REASON_MAX_LENGTH);
    }

    public void setTaskName(final String taskName) {
        this.taskName = truncate(taskName, TASK_NAME_MAX_LENGTH);
    }

    public GenAITraceRef getTraceRef() {
        GenAITraceRefType type;
        try {
            type = GenAITraceRefType.valueOf(refType);
        } catch (IllegalArgumentException | NullPointerException e) {
            type = segmentId == null ? GenAITraceRefType.OTLP : GenAITraceRefType.SKYWALKING_NATIVE;
        }
        return new GenAITraceRef(type, traceId, segmentId, spanIndex, spanId);
    }

    public GenAIEvaluationValueType getValueType() {
        if (valueType == null || valueType.isEmpty()) {
            return null;
        }
        try {
            return GenAIEvaluationValueType.valueOf(valueType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Long toScoreValuePpm(final Double score) {
        return score == null ? null : BigDecimal.valueOf(score)
                .movePointRight(6)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long minScoreValuePpm(final double score) {
        return BigDecimal.valueOf(score)
                .movePointRight(6)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    public static long maxScoreValuePpm(final double score) {
        return BigDecimal.valueOf(score)
                .movePointRight(6)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /**
     * Builds a stable storage identifier for one task evaluation of one span.
     * The judge response is intentionally excluded so retries overwrite the same record.
     */
    public static String toUniqueId(final String traceReference,
                                    final String taskName,
                                    final long evaluationTime) {
        final String identity = traceReference + "\u0000" + taskName + "\u0000" + evaluationTime;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    public static String toEntityId(final String name) {
        return IDManager.ServiceID.buildId(name, Layer.VIRTUAL_GENAI.isNormal());
    }

    public static String toServiceId(final String name) {
        return IDManager.ServiceID.buildId(name, Layer.GENAI.isNormal());
    }

    public static GenAIEvaluationRecord create(final String providerName, final String modelName) {
        final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
        record.providerId = toEntityId(providerName);
        record.modelId = IDManager.ServiceInstanceID.buildId(record.providerId, modelName);
        return record;
    }

    public String getServiceName() {
        return decodeName(serviceId);
    }

    public void setServiceName(final String serviceName) {
        this.serviceId = toServiceId(serviceName);
    }

    public String getProviderName() {
        return decodeName(providerId);
    }

    public String getModelName() {
        return modelId == null ? null : IDManager.ServiceInstanceID.analysisId(modelId).getName();
    }

    private static String truncate(final String value, final int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String decodeName(final String id) {
        return id == null ? null : IDManager.ServiceID.analysisId(id).getName();
    }

    @Override
    public StorageID id() {
        return new StorageID().append(UNIQUE_ID, uniqueId);
    }

    public static class Builder implements StorageBuilder<GenAIEvaluationRecord> {
        @Override
        public GenAIEvaluationRecord storage2Entity(final Convert2Entity converter) {
            final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
            record.setUniqueId((String) converter.get(UNIQUE_ID));
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setProviderId((String) converter.get(PROVIDER_ID));
            record.setModelId((String) converter.get(MODEL_ID));
            record.setOperationName((String) converter.get(OPERATION_NAME));
            final Number numberValue = (Number) converter.get(EVAL_NUMBER_VALUE);
            record.setEvalNumberValue(numberValue == null ? null : numberValue.longValue());
            record.setRefType((String) converter.get(REF_TYPE));
            record.setSegmentId((String) converter.get(SEGMENT_ID));
            final Number spanIndex = (Number) converter.get(SPAN_INDEX);
            record.setSpanIndex(spanIndex == null ? null : spanIndex.intValue());
            record.setSpanId((String) converter.get(SPAN_ID));
            record.setTaskName((String) converter.get(TASK_NAME));
            record.setValueType((String) converter.get(VALUE_TYPE));
            record.setEvalStringValue((String) converter.get(EVAL_STRING_VALUE));
            record.setEvaluationLevel((String) converter.get(EVALUATION_LEVEL));
            record.setReason((String) converter.get(REASON));
            record.setJudgeModel((String) converter.get(JUDGE_MODEL));
            record.setEvaluationTime(((Number) converter.get(EVALUATION_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final GenAIEvaluationRecord storageData, final Convert2Storage converter) {
            converter.accept(UNIQUE_ID, storageData.getUniqueId());
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(PROVIDER_ID, storageData.getProviderId());
            converter.accept(MODEL_ID, storageData.getModelId());
            converter.accept(OPERATION_NAME, storageData.getOperationName());
            converter.accept(EVAL_NUMBER_VALUE, storageData.getEvalNumberValue());
            converter.accept(REF_TYPE, storageData.refType);
            converter.accept(SEGMENT_ID, storageData.getSegmentId());
            converter.accept(SPAN_INDEX, storageData.getSpanIndex());
            converter.accept(SPAN_ID, storageData.getSpanId());
            converter.accept(TASK_NAME, storageData.getTaskName());
            converter.accept(VALUE_TYPE, storageData.valueType);
            converter.accept(EVAL_STRING_VALUE, storageData.getEvalStringValue());
            converter.accept(EVALUATION_LEVEL, storageData.getEvaluationLevel());
            converter.accept(REASON, storageData.getReason());
            converter.accept(JUDGE_MODEL, storageData.getJudgeModel());
            converter.accept(EVALUATION_TIME, storageData.getEvaluationTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
