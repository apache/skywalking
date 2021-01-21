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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameMaker;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

public class TraceQueryEsDAO extends EsDAO implements ITraceQueryDAO {

    private int segmentQueryMaxSize;

    public TraceQueryEsDAO(ElasticSearchClient client, int segmentQueryMaxSize) {
        super(client);
        this.segmentQueryMaxSize = segmentQueryMaxSize;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB,
                                       long endSecondTB,
                                       long minDuration,
                                       long maxDuration,
                                       String endpointName,
                                       String serviceId,
                                       String serviceInstanceId,
                                       String endpointId,
                                       String traceId,
                                       int limit,
                                       int from,
                                       TraceState traceState,
                                       QueryOrder queryOrder,
                                       final List<Tag> tags) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        if (startSecondTB != 0 && endSecondTB != 0) {
            mustQueryList.add(QueryBuilders.rangeQuery(SegmentRecord.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }

        if (minDuration != 0 || maxDuration != 0) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentRecord.LATENCY);
            if (minDuration != 0) {
                rangeQueryBuilder.gte(minDuration);
            }
            if (maxDuration != 0) {
                rangeQueryBuilder.lte(maxDuration);
            }
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (!Strings.isNullOrEmpty(endpointName)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(SegmentRecord.ENDPOINT_NAME);
            mustQueryList.add(QueryBuilders.matchPhraseQuery(matchCName, endpointName));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentRecord.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentRecord.ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentRecord.TRACE_ID, traceId));
        }
        switch (traceState) {
            case ERROR:
                mustQueryList.add(QueryBuilders.matchQuery(SegmentRecord.IS_ERROR, BooleanUtils.TRUE));
                break;
            case SUCCESS:
                mustQueryList.add(QueryBuilders.matchQuery(SegmentRecord.IS_ERROR, BooleanUtils.FALSE));
                break;
        }
        switch (queryOrder) {
            case BY_START_TIME:
                sourceBuilder.sort(SegmentRecord.START_TIME, SortOrder.DESC);
                break;
            case BY_DURATION:
                sourceBuilder.sort(SegmentRecord.LATENCY, SortOrder.DESC);
                break;
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            BoolQueryBuilder tagMatchQuery = QueryBuilders.boolQuery();
            tags.forEach(tag -> tagMatchQuery.must(QueryBuilders.termQuery(SegmentRecord.TAGS, tag.toString())));
            mustQueryList.add(tagMatchQuery);
        }
        sourceBuilder.size(limit);
        sourceBuilder.from(from);
        SearchResponse response = getClient().search(new TimeRangeIndexNameMaker(SegmentRecord.INDEX_NAME, startSecondTB, endSecondTB), sourceBuilder);
        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal((int) response.getHits().totalHits);

        for (SearchHit searchHit : response.getHits().getHits()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId((String) searchHit.getSourceAsMap().get(SegmentRecord.SEGMENT_ID));
            basicTrace.setStart(String.valueOf(searchHit.getSourceAsMap().get(SegmentRecord.START_TIME)));
            basicTrace.getEndpointNames().add((String) searchHit.getSourceAsMap().get(SegmentRecord.ENDPOINT_NAME));
            basicTrace.setDuration(((Number) searchHit.getSourceAsMap().get(SegmentRecord.LATENCY)).intValue());
            basicTrace.setError(
                BooleanUtils.valueToBoolean(
                    ((Number) searchHit.getSourceAsMap().get(SegmentRecord.IS_ERROR)).intValue()
                )
            );
            basicTrace.getTraceIds().add((String) searchHit.getSourceAsMap().get(SegmentRecord.TRACE_ID));
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.termQuery(SegmentRecord.TRACE_ID, traceId));
        sourceBuilder.size(segmentQueryMaxSize);

        SearchResponse response = getClient().search(SegmentRecord.INDEX_NAME, sourceBuilder);

        List<SegmentRecord> segmentRecords = new ArrayList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            SegmentRecord segmentRecord = new SegmentRecord();
            segmentRecord.setSegmentId((String) searchHit.getSourceAsMap().get(SegmentRecord.SEGMENT_ID));
            segmentRecord.setTraceId((String) searchHit.getSourceAsMap().get(SegmentRecord.TRACE_ID));
            segmentRecord.setServiceId((String) searchHit.getSourceAsMap().get(SegmentRecord.SERVICE_ID));
            segmentRecord.setEndpointName((String) searchHit.getSourceAsMap().get(SegmentRecord.ENDPOINT_NAME));
            segmentRecord.setStartTime(((Number) searchHit.getSourceAsMap().get(SegmentRecord.START_TIME)).longValue());
            segmentRecord.setEndTime(((Number) searchHit.getSourceAsMap().get(SegmentRecord.END_TIME)).longValue());
            segmentRecord.setLatency(((Number) searchHit.getSourceAsMap().get(SegmentRecord.LATENCY)).intValue());
            segmentRecord.setIsError(((Number) searchHit.getSourceAsMap().get(SegmentRecord.IS_ERROR)).intValue());
            String dataBinaryBase64 = (String) searchHit.getSourceAsMap().get(SegmentRecord.DATA_BINARY);
            if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
            }
            segmentRecord.setVersion(((Number) searchHit.getSourceAsMap().get(SegmentRecord.VERSION)).intValue());
            segmentRecords.add(segmentRecord);
        }
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }
}
