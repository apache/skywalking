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
import java.util.List;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotEmpty;

public class GenAIEvaluationRecordQueryEsDAO extends EsDAO implements IGenAIEvaluationRecordQueryDAO {
    private static final Set<String> QUERYABLE_TAG_KEYS = Set.of(
        GenAIEvaluationRecord.TRACE_ID,
        GenAIEvaluationRecord.SERVICE_ID,
        GenAIEvaluationRecord.SERVICE_INSTANCE_ID,
        GenAIEvaluationRecord.SEGMENT_ID,
        GenAIEvaluationRecord.SPAN_ID,
        GenAIEvaluationRecord.SPAN_TYPE,
        GenAIEvaluationRecord.TASK_NAME,
        GenAIEvaluationRecord.VALUE_TYPE,
        GenAIEvaluationRecord.VALUE,
        GenAIEvaluationRecord.EVALUATION_LEVEL,
        GenAIEvaluationRecord.REASON,
        GenAIEvaluationRecord.JUDGE_MODEL
    );

    public GenAIEvaluationRecordQueryEsDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public GenAIEvaluationRecords queryGenAIEvaluationRecord(final String serviceId,
                                                             final String serviceInstanceId,
                                                             final TraceScopeCondition relatedTrace,
                                                             final Order queryOrder,
                                                             final int from,
                                                             final int limit,
                                                             final Duration duration,
                                                             final List<Tag> tags) throws IOException {
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
        if (isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(GenAIEvaluationRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (nonNull(relatedTrace)) {
            if (isNotEmpty(relatedTrace.getTraceId())) {
                query.must(Query.term(GenAIEvaluationRecord.TRACE_ID, relatedTrace.getTraceId()));
            }
            if (isNotEmpty(relatedTrace.getSegmentId())) {
                query.must(Query.term(GenAIEvaluationRecord.SEGMENT_ID, relatedTrace.getSegmentId()));
            }
            if (nonNull(relatedTrace.getSpanId())) {
                query.must(Query.term(GenAIEvaluationRecord.SPAN_ID, String.valueOf(relatedTrace.getSpanId())));
            }
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                if (isNotEmpty(tag.getKey()) && isNotEmpty(tag.getValue())) {
                    if (!QUERYABLE_TAG_KEYS.contains(tag.getKey())) {
                        return new GenAIEvaluationRecords();
                    }
                    query.must(Query.term(tag.getKey(), tag.getValue()));
                }
            }
        }

        final SearchBuilder search = Search.builder()
                                           .query(query)
                                           .sort(
                                               GenAIEvaluationRecord.EVALUATION_TIME,
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
        record.setTraceId((String) source.get(GenAIEvaluationRecord.TRACE_ID));
        record.setServiceId((String) source.get(GenAIEvaluationRecord.SERVICE_ID));
        record.setServiceInstanceId((String) source.get(GenAIEvaluationRecord.SERVICE_INSTANCE_ID));
        record.setSegmentId((String) source.get(GenAIEvaluationRecord.SEGMENT_ID));
        record.setSpanId((String) source.get(GenAIEvaluationRecord.SPAN_ID));
        record.setSpanType((String) source.get(GenAIEvaluationRecord.SPAN_TYPE));
        record.setTaskName((String) source.get(GenAIEvaluationRecord.TASK_NAME));
        record.setValueType((String) source.get(GenAIEvaluationRecord.VALUE_TYPE));
        record.setValue((String) source.get(GenAIEvaluationRecord.VALUE));
        record.setEvaluationLevel((String) source.get(GenAIEvaluationRecord.EVALUATION_LEVEL));
        record.setReason((String) source.get(GenAIEvaluationRecord.REASON));
        record.setJudgeModel((String) source.get(GenAIEvaluationRecord.JUDGE_MODEL));
        record.setEvaluationTime(((Number) source.get(GenAIEvaluationRecord.EVALUATION_TIME)).longValue());
        return record;
    }
}
