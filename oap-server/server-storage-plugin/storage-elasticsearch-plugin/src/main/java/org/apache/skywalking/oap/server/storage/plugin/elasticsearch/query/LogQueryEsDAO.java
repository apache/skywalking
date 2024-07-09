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

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotEmpty;

public class LogQueryEsDAO extends EsDAO implements ILogQueryDAO {
    public LogQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public boolean supportQueryLogsByKeywords() {
        return true;
    }

    @Override
    public Logs queryLogs(final String serviceId,
                          final String serviceInstanceId,
                          final String endpointId,
                          final TraceScopeCondition relatedTrace,
                          final Order queryOrder,
                          final int from,
                          final int limit,
                          final Duration duration,
                          final List<Tag> tags,
                          final List<String> keywordsOfContent,
                          final List<String> excludingKeywordsOfContent) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(LogRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, LogRecord.INDEX_NAME));
        }
        if (startSecondTB != 0 && endSecondTB != 0) {
            query.must(Query.range(Record.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }
        if (isNotEmpty(serviceId)) {
            query.must(Query.term(AbstractLogRecord.SERVICE_ID, serviceId));
        }
        if (isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (isNotEmpty(endpointId)) {
            query.must(Query.term(AbstractLogRecord.ENDPOINT_ID, endpointId));
        }
        if (nonNull(relatedTrace)) {
            if (isNotEmpty(relatedTrace.getTraceId())) {
                query.must(Query.term(AbstractLogRecord.TRACE_ID, relatedTrace.getTraceId()));
            }
            if (isNotEmpty(relatedTrace.getSegmentId())) {
                query.must(
                    Query.term(AbstractLogRecord.TRACE_SEGMENT_ID, relatedTrace.getSegmentId()));
            }
            if (nonNull(relatedTrace.getSpanId())) {
                query.must(Query.term(AbstractLogRecord.SPAN_ID, relatedTrace.getSpanId()));
            }
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            tags.forEach(tag -> query.must(Query.term(AbstractLogRecord.TAGS, tag.toString())));
        }

        if (CollectionUtils.isNotEmpty(keywordsOfContent)) {
            keywordsOfContent.forEach(
                content ->
                    query.must(
                        Query.matchPhrase(
                            MatchCNameBuilder.INSTANCE.build(AbstractLogRecord.CONTENT),
                            content
                        )
                    )
            );
        }

        if (CollectionUtils.isNotEmpty(excludingKeywordsOfContent)) {
            excludingKeywordsOfContent.forEach(
                content ->
                    query.mustNot(
                        Query.matchPhrase(
                            MatchCNameBuilder.INSTANCE.build(AbstractLogRecord.CONTENT),
                            content
                        )
                    )
            );
        }

        final SearchBuilder search =
            Search.builder().query(query)
                  .sort(
                      LogRecord.TIMESTAMP,
                      Order.DES.equals(queryOrder) ?
                          Sort.Order.DESC : Sort.Order.ASC
                  )
                  .size(limit)
                  .from(from);

        SearchResponse response = searchDebuggable(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(LogRecord.INDEX_NAME),
            startSecondTB,
            endSecondTB
        ), search.build());

        Logs logs = new Logs();

        for (SearchHit searchHit : response.getHits().getHits()) {
            Log log = new Log();
            log.setServiceId((String) searchHit.getSource().get(AbstractLogRecord.SERVICE_ID));
            log.setServiceInstanceId((String) searchHit.getSource()
                                                       .get(AbstractLogRecord.SERVICE_INSTANCE_ID));
            log.setEndpointId(
                (String) searchHit.getSource().get(AbstractLogRecord.ENDPOINT_ID));
            if (log.getEndpointId() != null) {
                log.setEndpointName(
                    IDManager.EndpointID.analysisId(log.getEndpointId()).getEndpointName());
            }
            log.setTraceId((String) searchHit.getSource().get(AbstractLogRecord.TRACE_ID));
            log.setTimestamp(
                ((Number) searchHit.getSource().get(AbstractLogRecord.TIMESTAMP)).longValue());
            log.setContentType(ContentType.instanceOf(
                ((Number) searchHit.getSource()
                                   .get(AbstractLogRecord.CONTENT_TYPE)).intValue()));
            log.setContent((String) searchHit.getSource().get(AbstractLogRecord.CONTENT));
            String dataBinaryBase64 =
                (String) searchHit.getSource().get(AbstractLogRecord.TAGS_RAW_DATA);
            if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                parserDataBinary(dataBinaryBase64, log.getTags());
            }
            logs.getLogs().add(log);
        }
        return logs;
    }
}
