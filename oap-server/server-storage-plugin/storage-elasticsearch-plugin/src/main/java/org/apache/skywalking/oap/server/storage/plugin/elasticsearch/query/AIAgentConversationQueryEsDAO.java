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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

/**
 * Both models are super datasets, so each has its own index family; the queries are term filters on the
 * series-id columns plus a range on <code>seq</code> or on the timestamp. The list read leaves the body out of the
 * returned source.
 */
public class AIAgentConversationQueryEsDAO extends EsDAO implements IAIAgentConversationQueryDAO {
    /** Elasticsearch's default result window; a seq or round window of 16 is far below it, copies included. */
    private static final int RESULT_WINDOW = 10_000;
    private static final String[] ROUND_FIELDS = {
        AIAgentSessionFlowRecord.SERVICE_ID,
        AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID,
        AIAgentSessionFlowRecord.CONVERSATION,
        AIAgentSessionFlowRecord.ROUND,
        AIAgentSessionFlowRecord.SESSION_FROM_TIME,
        AIAgentSessionFlowRecord.TITLE,
        AIAgentSessionFlowRecord.TALKS,
        AIAgentSessionFlowRecord.STEPS,
        AIAgentSessionFlowRecord.STREAMS,
        AIAgentSessionFlowRecord.SEGMENTS,
        AIAgentSessionFlowRecord.UNRESOLVED,
        AIAgentSessionFlowRecord.DIGEST,
        AIAgentSessionFlowRecord.TIMESTAMP
    };

    public AIAgentConversationQueryEsDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<AIAgentSessionFlowRecord> queryRounds(final String serviceId,
                                                      @Nullable final String serviceInstanceId,
                                                      @Nullable final String conversation,
                                                      @Nullable final Duration duration,
                                                      final int limit,
                                                      final boolean includeBody) throws IOException {
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AIAgentSessionFlowRecord.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AIAgentSessionFlowRecord.INDEX_NAME));
        }
        query.must(Query.term(AIAgentSessionFlowRecord.SERVICE_ID, serviceId));
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (StringUtil.isNotEmpty(conversation)) {
            query.must(Query.term(AIAgentSessionFlowRecord.CONVERSATION, conversation));
        }
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (duration != null) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
            query.must(Query.range(Record.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }
        final SearchBuilder search = Search.builder()
                                           .query(query)
                                           .sort(AIAgentSessionFlowRecord.TIMESTAMP, Sort.Order.DESC)
                                           .size(limit);
        if (!includeBody) {
            for (final String field : ROUND_FIELDS) {
                search.source(field);
            }
        }
        final SearchResponse response = searchDebuggable(
            new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(AIAgentSessionFlowRecord.INDEX_NAME),
                startSecondTB, endSecondTB
            ),
            search.build()
        );
        return rounds(response, includeBody);
    }

    @Override
    public List<AIAgentSessionFlowRecord> queryRoundsByNumber(final String serviceId,
                                                              @Nullable final String serviceInstanceId,
                                                              final String conversation,
                                                              final long fromRound,
                                                              final long throughRound) throws IOException {
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AIAgentSessionFlowRecord.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AIAgentSessionFlowRecord.INDEX_NAME));
        }
        query.must(Query.term(AIAgentSessionFlowRecord.SERVICE_ID, serviceId));
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        query.must(Query.term(AIAgentSessionFlowRecord.CONVERSATION, conversation));
        query.must(Query.range(AIAgentSessionFlowRecord.ROUND).gte(fromRound).lte(throughRound));
        final SearchBuilder search = Search.builder()
                                           .query(query)
                                           .sort(AIAgentSessionFlowRecord.ROUND, Sort.Order.ASC)
                                           // every row of the window: a round two senders both pushed is there twice
                                           .size(RESULT_WINDOW);
        // every index of the family: the rounds of a long conversation span days
        final SearchResponse response = searchDebuggable(
            new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(AIAgentSessionFlowRecord.INDEX_NAME), 0, 0),
            search.build()
        );
        return rounds(response, true);
    }

    private static List<AIAgentSessionFlowRecord> rounds(final SearchResponse response, final boolean includeBody) {
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>();
        for (final SearchHit hit : response.getHits().getHits()) {
            final Map<String, Object> source = hit.getSource();
            final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
            record.setServiceId((String) source.get(AIAgentSessionFlowRecord.SERVICE_ID));
            record.setServiceInstanceId((String) source.get(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID));
            record.setConversation((String) source.get(AIAgentSessionFlowRecord.CONVERSATION));
            record.setRound(longOf(source.get(AIAgentSessionFlowRecord.ROUND)));
            record.setSessionFromTime(longOf(source.get(AIAgentSessionFlowRecord.SESSION_FROM_TIME)));
            record.setTitle((String) source.get(AIAgentSessionFlowRecord.TITLE));
            record.setTalks(longOf(source.get(AIAgentSessionFlowRecord.TALKS)));
            record.setSteps(longOf(source.get(AIAgentSessionFlowRecord.STEPS)));
            record.setStreams(longOf(source.get(AIAgentSessionFlowRecord.STREAMS)));
            record.setSegments(longOf(source.get(AIAgentSessionFlowRecord.SEGMENTS)));
            record.setUnresolved(longOf(source.get(AIAgentSessionFlowRecord.UNRESOLVED)));
            record.setDigest((String) source.get(AIAgentSessionFlowRecord.DIGEST));
            final long timestamp = longOf(source.get(AIAgentSessionFlowRecord.TIMESTAMP));
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            if (includeBody) {
                record.setBody(bytesOf(source.get(AIAgentSessionFlowRecord.BODY)));
            }
            rounds.add(record);
        }
        return rounds;
    }

    @Override
    public List<AIAgentSessionDataRecord> queryFiles(final String serviceId,
                                                     @Nullable final String serviceInstanceId,
                                                     final String session,
                                                     final long fromTimestamp,
                                                     final long toTimestamp,
                                                     final long fromSeq,
                                                     final long throughSeq) throws IOException {
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AIAgentSessionDataRecord.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AIAgentSessionDataRecord.INDEX_NAME));
        }
        query.must(Query.term(AIAgentSessionDataRecord.SERVICE_ID, serviceId));
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(AIAgentSessionDataRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        query.must(Query.term(AIAgentSessionDataRecord.SESSION, session));
        query.must(Query.range(AIAgentSessionDataRecord.SEQ).gte(fromSeq).lte(throughSeq));
        query.must(Query.range(AIAgentSessionDataRecord.TIMESTAMP).gte(fromTimestamp).lte(toTimestamp));
        final long startSecondTB = TimeBucket.getRecordTimeBucket(fromTimestamp);
        final long endSecondTB = TimeBucket.getRecordTimeBucket(toTimestamp);
        final SearchBuilder search = Search.builder()
                                           .query(query)
                                           .sort(AIAgentSessionDataRecord.SEQ, Sort.Order.ASC)
                                           .size(RESULT_WINDOW);
        final SearchResponse response = searchDebuggable(
            new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(AIAgentSessionDataRecord.INDEX_NAME),
                startSecondTB, endSecondTB
            ),
            search.build()
        );
        final List<AIAgentSessionDataRecord> files = new ArrayList<>();
        for (final SearchHit hit : response.getHits().getHits()) {
            final Map<String, Object> source = hit.getSource();
            final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
            record.setServiceId((String) source.get(AIAgentSessionDataRecord.SERVICE_ID));
            record.setServiceInstanceId((String) source.get(AIAgentSessionDataRecord.SERVICE_INSTANCE_ID));
            record.setSession((String) source.get(AIAgentSessionDataRecord.SESSION));
            record.setSeq(longOf(source.get(AIAgentSessionDataRecord.SEQ)));
            record.setDigest((String) source.get(AIAgentSessionDataRecord.DIGEST));
            final long timestamp = longOf(source.get(AIAgentSessionDataRecord.TIMESTAMP));
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            record.setBody(bytesOf(source.get(AIAgentSessionDataRecord.BODY)));
            files.add(record);
        }
        return files;
    }

    private static long longOf(final Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * A <code>byte[]</code> column is written as base64 text, the way the segment and browser log bodies are.
     */
    private static byte[] bytesOf(final Object value) {
        if (value == null) {
            return null;
        }
        return Base64.getDecoder().decode((String) value);
    }
}
