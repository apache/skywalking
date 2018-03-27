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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.apache.skywalking.apm.collector.storage.ui.trace.BasicTrace;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationEsUIDAO extends EsDAO implements ISegmentDurationUIDAO {

    public SegmentDurationEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public TraceBrief loadTop(long startSecondTimeBucket, long endSecondTimeBucket, long minDuration, long maxDuration,
        String operationName, int applicationId, int limit, int from, String... segmentIds) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(SegmentDurationTable.TABLE);
        searchRequestBuilder.setTypes(SegmentDurationTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        searchRequestBuilder.setQuery(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        if (startSecondTimeBucket != 0 && endSecondTimeBucket != 0) {
            //TODO second
            mustQueryList.add(QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_TIME_BUCKET).gte(startSecondTimeBucket).lte(endSecondTimeBucket));
        }

        if (minDuration != 0 || maxDuration != 0) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_DURATION);
            if (minDuration != 0) {
                rangeQueryBuilder.gte(minDuration);
            }
            if (maxDuration != 0) {
                rangeQueryBuilder.lte(maxDuration);
            }
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (StringUtils.isNotEmpty(operationName)) {
            mustQueryList.add(QueryBuilders.matchQuery(SegmentDurationTable.COLUMN_SERVICE_NAME, operationName));
        }
        if (CollectionUtils.isNotEmpty(segmentIds)) {
            boolQueryBuilder.must().add(QueryBuilders.termsQuery(SegmentDurationTable.COLUMN_SEGMENT_ID, segmentIds));
        }
        if (applicationId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentDurationTable.COLUMN_APPLICATION_ID, applicationId));
        }

        searchRequestBuilder.setSize(limit);
        searchRequestBuilder.setFrom(from);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal((int)searchResponse.getHits().totalHits);

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId((String)searchHit.getSource().get(SegmentDurationTable.COLUMN_SEGMENT_ID));
            basicTrace.setStart(((Number)searchHit.getSource().get(SegmentDurationTable.COLUMN_START_TIME)).longValue());
            basicTrace.setOperationName((String)searchHit.getSource().get(SegmentDurationTable.COLUMN_SERVICE_NAME));
            basicTrace.setDuration(((Number)searchHit.getSource().get(SegmentDurationTable.COLUMN_DURATION)).intValue());
            basicTrace.setError(BooleanUtils.valueToBoolean(((Number)searchHit.getSource().get(SegmentDurationTable.COLUMN_IS_ERROR)).intValue()));
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }
}
