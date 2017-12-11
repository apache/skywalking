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


package org.apache.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCostTable;

/**
 * @author peng-yongsheng
 */
public class SegmentCostEsUIDAO extends EsDAO implements ISegmentCostUIDAO {

    public SegmentCostEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        Error error, int applicationId, List<String> segmentIds, int limit, int from, Sort sort) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(SegmentCostTable.TABLE);
        searchRequestBuilder.setTypes(SegmentCostTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        searchRequestBuilder.setQuery(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        mustQueryList.add(QueryBuilders.rangeQuery(SegmentCostTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        if (minCost != -1 || maxCost != -1) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentCostTable.COLUMN_COST);
            if (minCost != -1) {
                rangeQueryBuilder.gte(minCost);
            }
            if (maxCost != -1) {
                rangeQueryBuilder.lte(maxCost);
            }
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (StringUtils.isNotEmpty(operationName)) {
            mustQueryList.add(QueryBuilders.matchQuery(SegmentCostTable.COLUMN_SERVICE_NAME, operationName));
        }
        if (CollectionUtils.isNotEmpty(segmentIds)) {
            boolQueryBuilder.must().add(QueryBuilders.termsQuery(SegmentCostTable.COLUMN_SEGMENT_ID, segmentIds.toArray(new String[0])));
        }
        if (Error.True.equals(error)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentCostTable.COLUMN_IS_ERROR, true));
        } else if (Error.False.equals(error)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentCostTable.COLUMN_IS_ERROR, false));
        }
        if (applicationId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentCostTable.COLUMN_APPLICATION_ID, applicationId));
        }

        if (Sort.Cost.equals(sort)) {
            searchRequestBuilder.addSort(SegmentCostTable.COLUMN_COST, SortOrder.DESC);
        } else if (Sort.Time.equals(sort)) {
            searchRequestBuilder.addSort(SegmentCostTable.COLUMN_START_TIME, SortOrder.DESC);
        }
        searchRequestBuilder.setSize(limit);
        searchRequestBuilder.setFrom(from);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        JsonObject topSegPaging = new JsonObject();
        topSegPaging.addProperty("recordsTotal", searchResponse.getHits().totalHits);

        JsonArray topSegArray = new JsonArray();
        topSegPaging.add("data", topSegArray);

        int num = from;
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            JsonObject topSegmentJson = new JsonObject();
            topSegmentJson.addProperty("num", num);
            String segmentId = (String)searchHit.getSource().get(SegmentCostTable.COLUMN_SEGMENT_ID);
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_SEGMENT_ID, segmentId);
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_START_TIME, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_START_TIME));
            if (searchHit.getSource().containsKey(SegmentCostTable.COLUMN_END_TIME)) {
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_END_TIME, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_END_TIME));
            }

            topSegmentJson.addProperty(SegmentCostTable.COLUMN_APPLICATION_ID, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_APPLICATION_ID));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_SERVICE_NAME, (String)searchHit.getSource().get(SegmentCostTable.COLUMN_SERVICE_NAME));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_COST, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_COST));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_IS_ERROR, (Boolean)searchHit.getSource().get(SegmentCostTable.COLUMN_IS_ERROR));

            num++;
            topSegArray.add(topSegmentJson);
        }

        return topSegPaging;
    }
}
