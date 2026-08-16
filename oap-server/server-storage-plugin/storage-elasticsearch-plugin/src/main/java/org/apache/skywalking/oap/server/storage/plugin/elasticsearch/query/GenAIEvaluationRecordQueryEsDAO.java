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
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotEmpty;

public class GenAIEvaluationRecordQueryEsDAO extends EsDAO implements IGenAIEvaluationRecordQueryDAO {
    public GenAIEvaluationRecordQueryEsDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public GenAIEvaluationRecords queryGenAIEvaluationRecord(final String serviceId,
                                                             final String providerId,
                                                             final String modelId,
                                                             final GenAIEvaluationValueType valueType,
                                                             final Long minScore, final Long maxScore, final Boolean booleanValue,
                                                             final GenAIEvaluationRecordSortBy sortBy,
                                                             final String taskName, final String evaluationLevel, final String judgeModel,
                                                             final TraceScopeCondition relatedTrace,
                                                             final Order queryOrder,
                                                             final int from,
                                                             final int limit,
                                                             final Duration duration) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }

        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(GenAIEvaluationRecord.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.RECORD_TABLE_NAME,
                GenAIEvaluationRecord.INDEX_NAME
            ));
        }
        if (startSecondTB != 0 && endSecondTB != 0) {
            query.must(Query.range(Record.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }
        if (isNotEmpty(serviceId)) {
            query.must(Query.term(GenAIEvaluationRecord.SERVICE_ID, serviceId));
        }
        if (isNotEmpty(providerId)) {
            query.must(Query.term(GenAIEvaluationRecord.PROVIDER_ID, providerId));
        }
        if (isNotEmpty(modelId)) {
            query.must(Query.term(GenAIEvaluationRecord.MODEL_ID, modelId));
        }
        if (valueType != null) {
            query.must(Query.term(GenAIEvaluationRecord.VALUE_TYPE, valueType.name()));
        }
        if (booleanValue != null) {
            query.must(Query.term(GenAIEvaluationRecord.EVAL_NUMBER_VALUE, booleanValue ? GenAIEvaluationRecord.SCORE_SCALE : 0));
        } else if (minScore != null || maxScore != null) {
            final var scoreRange = Query.range(GenAIEvaluationRecord.EVAL_NUMBER_VALUE);
            if (minScore != null) scoreRange.gte(minScore);
            if (maxScore != null) scoreRange.lte(maxScore);
            query.must(scoreRange);
        }
        if (isNotEmpty(taskName)) {
            query.must(Query.term(GenAIEvaluationRecord.TASK_NAME, taskName));
        }
        if (isNotEmpty(evaluationLevel)) {
            query.must(Query.term(GenAIEvaluationRecord.EVALUATION_LEVEL, evaluationLevel));
        }
        if (isNotEmpty(judgeModel)) {
            query.must(Query.term(GenAIEvaluationRecord.JUDGE_MODEL, judgeModel));
        }
        if (nonNull(relatedTrace)) {
            if (isNotEmpty(relatedTrace.getTraceId())) {
                query.must(Query.term(GenAIEvaluationRecord.TRACE_ID, relatedTrace.getTraceId()));
            }
            if (isNotEmpty(relatedTrace.getSegmentId())) {
                query.must(Query.term(GenAIEvaluationRecord.SEGMENT_ID, relatedTrace.getSegmentId()));
            }
            if (nonNull(relatedTrace.getSpanId())) {
                query.must(Query.term(GenAIEvaluationRecord.SPAN_INDEX, relatedTrace.getSpanId()));
            }
        }
        final SearchBuilder search = Search.builder()
                                           .query(query)
                                           .sort(
                                               GenAIEvaluationRecordSortBy.SCORE_VALUE.equals(sortBy) ? GenAIEvaluationRecord.EVAL_NUMBER_VALUE : GenAIEvaluationRecord.EVALUATION_TIME,
                                               Order.DES.equals(queryOrder) ? Sort.Order.DESC : Sort.Order.ASC
                                           )
                                           .size(limit)
                                           .from(from);

        final SearchResponse response = searchDebuggable(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(GenAIEvaluationRecord.INDEX_NAME),
            startSecondTB,
            endSecondTB
        ), search.build());

        final GenAIEvaluationRecords records = new GenAIEvaluationRecords();
        for (SearchHit searchHit : response.getHits().getHits()) {
            records.getGenAIEvaluationRecordList().add(parseRecord(searchHit));
        }
        return records;
    }

    private GenAIEvaluationRecord parseRecord(final SearchHit searchHit) {
        final var source = searchHit.getSource();
        final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
        record.setUniqueId((String) source.get(GenAIEvaluationRecord.UNIQUE_ID));
        record.setTraceId((String) source.get(GenAIEvaluationRecord.TRACE_ID));
        record.setServiceId((String) source.get(GenAIEvaluationRecord.SERVICE_ID));
        record.setProviderId((String) source.get(GenAIEvaluationRecord.PROVIDER_ID));
        record.setModelId((String) source.get(GenAIEvaluationRecord.MODEL_ID));
        record.setOperationName((String) source.get(GenAIEvaluationRecord.OPERATION_NAME));
        final Number numberValue = (Number) source.get(GenAIEvaluationRecord.EVAL_NUMBER_VALUE);
        record.setEvalNumberValue(numberValue == null ? null : numberValue.longValue());
        record.setRefType((String) source.get(GenAIEvaluationRecord.REF_TYPE));
        record.setSegmentId((String) source.get(GenAIEvaluationRecord.SEGMENT_ID));
        final Number spanIndex = (Number) source.get(GenAIEvaluationRecord.SPAN_INDEX);
        record.setSpanIndex(spanIndex == null ? null : spanIndex.intValue());
        record.setSpanId((String) source.get(GenAIEvaluationRecord.SPAN_ID));
        record.setTaskName((String) source.get(GenAIEvaluationRecord.TASK_NAME));
        record.setValueType((String) source.get(GenAIEvaluationRecord.VALUE_TYPE));
        record.setEvalStringValue((String) source.get(GenAIEvaluationRecord.EVAL_STRING_VALUE));
        record.setEvaluationLevel((String) source.get(GenAIEvaluationRecord.EVALUATION_LEVEL));
        record.setReason((String) source.get(GenAIEvaluationRecord.REASON));
        record.setJudgeModel((String) source.get(GenAIEvaluationRecord.JUDGE_MODEL));
        record.setEvaluationTime(((Number) source.get(GenAIEvaluationRecord.EVALUATION_TIME)).longValue());
        return record;
    }
}
