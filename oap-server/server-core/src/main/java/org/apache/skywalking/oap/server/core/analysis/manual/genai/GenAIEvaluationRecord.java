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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
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
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String SEGMENT_ID = "segment_id";
    public static final String SPAN_ID = "span_id";
    public static final String SPAN_TYPE = "span_type";
    public static final String TASK_NAME = "task_name";
    public static final String VALUE_TYPE = "value_type";
    public static final String VALUE = "value";
    public static final String EVALUATION_LEVEL = "evaluation_level";
    public static final String REASON = "reason";
    public static final String JUDGE_MODEL = "judge_model";
    public static final String EVALUATION_TIME = "evaluation_time";

    @Column(name = UNIQUE_ID)
    private String uniqueId;

    @Column(name = TRACE_ID, length = 150)
    @BanyanDB.IndexRule(indexType = BanyanDB.IndexRule.IndexType.SKIPPING)
    private String traceId;

    @ElasticSearch.EnableDocValues
    @Column(name = SERVICE_ID, length = 150, storageOnly = true)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;

    @ElasticSearch.EnableDocValues
    @Column(name = SERVICE_INSTANCE_ID, length = 150, storageOnly = true)
    @BanyanDB.SeriesID(index = 1)
    private String serviceInstanceId;

    @Column(name = SEGMENT_ID, length = 150, storageOnly = true)
    private String segmentId;

    @Column(name = SPAN_ID, length = 150, storageOnly = true)
    private String spanId;

    @Column(name = SPAN_TYPE, length = 64)
    private String spanType;

    @ElasticSearch.EnableDocValues
    @Column(name = TASK_NAME, length = 512)
    private String taskName;

    @Column(name = VALUE_TYPE, length = 64, storageOnly = true)
    private String valueType;

    @Column(name = VALUE, length = 4096, storageOnly = true)
    private String value;

    @ElasticSearch.EnableDocValues
    @Column(name = EVALUATION_LEVEL, length = 64)
    @BanyanDB.IndexRule(indexType = BanyanDB.IndexRule.IndexType.SKIPPING)
    private String evaluationLevel;

    @Column(name = REASON, length = 4096, storageOnly = true)
    private String reason;

    @Column(name = JUDGE_MODEL, length = 64, storageOnly = true)
    private String judgeModel;

    @ElasticSearch.EnableDocValues
    @Column(name = EVALUATION_TIME)
    private long evaluationTime;

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
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setSegmentId((String) converter.get(SEGMENT_ID));
            record.setSpanId((String) converter.get(SPAN_ID));
            record.setSpanType((String) converter.get(SPAN_TYPE));
            record.setTaskName((String) converter.get(TASK_NAME));
            record.setValueType((String) converter.get(VALUE_TYPE));
            record.setValue((String) converter.get(VALUE));
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
            converter.accept(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
            converter.accept(SEGMENT_ID, storageData.getSegmentId());
            converter.accept(SPAN_ID, storageData.getSpanId());
            converter.accept(SPAN_TYPE, storageData.getSpanType());
            converter.accept(TASK_NAME, storageData.getTaskName());
            converter.accept(VALUE_TYPE, storageData.getValueType());
            converter.accept(VALUE, storageData.getValue());
            converter.accept(EVALUATION_LEVEL, storageData.getEvaluationLevel());
            converter.accept(REASON, storageData.getReason());
            converter.accept(JUDGE_MODEL, storageData.getJudgeModel());
            converter.accept(EVALUATION_TIME, storageData.getEvaluationTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
