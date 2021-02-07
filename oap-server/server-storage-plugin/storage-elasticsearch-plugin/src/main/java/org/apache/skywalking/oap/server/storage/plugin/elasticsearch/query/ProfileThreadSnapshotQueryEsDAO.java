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
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ProfileThreadSnapshotQueryEsDAO extends EsDAO implements IProfileThreadSnapshotQueryDAO {

    private final int querySegmentMaxSize;

    protected final ProfileThreadSnapshotRecord.Builder builder = new ProfileThreadSnapshotRecord.Builder();

    public ProfileThreadSnapshotQueryEsDAO(ElasticSearchClient client, int profileTaskQueryMaxSize) {
        super(client);
        this.querySegmentMaxSize = profileTaskQueryMaxSize;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        // search segment id list
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.TASK_ID, taskId));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.SEQUENCE, 0));

        sourceBuilder.size(querySegmentMaxSize);
        sourceBuilder.sort(ProfileThreadSnapshotRecord.DUMP_TIME, SortOrder.DESC);

        SearchResponse response = getClient().search(ProfileThreadSnapshotRecord.INDEX_NAME, sourceBuilder);

        final LinkedList<String> segments = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            segments.add((String) searchHit.getSourceAsMap().get(ProfileThreadSnapshotRecord.SEGMENT_ID));
        }

        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }

        // search traces
        sourceBuilder = SearchSourceBuilder.searchSource();

        boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> shouldQueryList = boolQueryBuilder.should();

        for (String segmentId : segments) {
            shouldQueryList.add(QueryBuilders.termQuery(SegmentRecord.SEGMENT_ID, segmentId));
        }
        sourceBuilder.size(segments.size());
        sourceBuilder.sort(SegmentRecord.START_TIME, SortOrder.DESC);

        response = getClient().search(SegmentRecord.INDEX_NAME, sourceBuilder);

        List<BasicTrace> result = new ArrayList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId((String) searchHit.getSourceAsMap().get(SegmentRecord.SEGMENT_ID));
            basicTrace.setStart(String.valueOf(searchHit.getSourceAsMap().get(SegmentRecord.START_TIME)));
            basicTrace.getEndpointNames().add((String) searchHit.getSourceAsMap().get(SegmentRecord.ENDPOINT_NAME));
            basicTrace.setDuration(((Number) searchHit.getSourceAsMap().get(SegmentRecord.LATENCY)).intValue());
            basicTrace.setError(BooleanUtils.valueToBoolean(((Number) searchHit.getSourceAsMap()
                                                                               .get(SegmentRecord.IS_ERROR)).intValue()));
            basicTrace.getTraceIds().add((String) searchHit.getSourceAsMap().get(SegmentRecord.TRACE_ID));

            result.add(basicTrace);
        }

        return result;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggregationBuilders.min(ProfileThreadSnapshotRecord.SEQUENCE).field(ProfileThreadSnapshotRecord.SEQUENCE), segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggregationBuilders.max(ProfileThreadSnapshotRecord.SEQUENCE).field(ProfileThreadSnapshotRecord.SEQUENCE), segmentId, start, end);
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        // search traces
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        mustQueryList.add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId));
        mustQueryList.add(QueryBuilders.rangeQuery(ProfileThreadSnapshotRecord.SEQUENCE).gte(minSequence).lt(maxSequence));
        sourceBuilder.size(maxSequence - minSequence);

        SearchResponse response = getClient().search(ProfileThreadSnapshotRecord.INDEX_NAME, sourceBuilder);

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        for (SearchHit searchHit : response.getHits().getHits()) {
            ProfileThreadSnapshotRecord record = builder.storage2Entity(searchHit.getSourceAsMap());

            result.add(record);
        }
        return result;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.termQuery(SegmentRecord.SEGMENT_ID, segmentId));
        sourceBuilder.size(1);

        SearchResponse response = getClient().search(SegmentRecord.INDEX_NAME, sourceBuilder);

        if (response.getHits().getHits().length == 0) {
            return null;
        }
        SearchHit searchHit = response.getHits().getHits()[0];
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
        return segmentRecord;
    }

    protected int querySequenceWithAgg(AbstractAggregationBuilder aggregationBuilder, String segmentId, long start, long end) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        mustQueryList.add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId));
        mustQueryList.add(QueryBuilders.rangeQuery(ProfileThreadSnapshotRecord.DUMP_TIME).gte(start).lte(end));
        sourceBuilder.size(0);

        sourceBuilder.aggregation(aggregationBuilder);
        SearchResponse response = getClient().search(ProfileThreadSnapshotRecord.INDEX_NAME, sourceBuilder);
        NumericMetricsAggregation.SingleValue agg = response.getAggregations().get(ProfileThreadSnapshotRecord.SEQUENCE);

        return (int) agg.value();
    }
}
