/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.sort.SortOrder;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.instance.InstPerformanceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author peng-yongsheng
 */
public class InstPerformanceEsDAO extends EsDAO implements IInstPerformanceDAO {

    @Override public InstPerformance get(long[] timeBuckets, int instanceId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstPerformanceTable.TABLE);
        searchRequestBuilder.setTypes(InstPerformanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(InstPerformanceTable.COLUMN_INSTANCE_ID, instanceId));
        boolQuery.must().add(QueryBuilders.termsQuery(InstPerformanceTable.COLUMN_TIME_BUCKET, timeBuckets));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addSort(InstPerformanceTable.COLUMN_INSTANCE_ID, SortOrder.ASC);

        searchRequestBuilder.addAggregation(AggregationBuilders.sum(InstPerformanceTable.COLUMN_CALLS).field(InstPerformanceTable.COLUMN_CALLS));
        searchRequestBuilder.addAggregation(AggregationBuilders.sum(InstPerformanceTable.COLUMN_COST_TOTAL).field(InstPerformanceTable.COLUMN_COST_TOTAL));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Sum sumCalls = searchResponse.getAggregations().get(InstPerformanceTable.COLUMN_CALLS);
        Sum sumCostTotal = searchResponse.getAggregations().get(InstPerformanceTable.COLUMN_CALLS);
        return new InstPerformance(instanceId, (int)sumCalls.getValue(), (long)sumCostTotal.getValue());
    }

    @Override public int getTpsMetric(int instanceId, long timeBucket) {
        String id = timeBucket + Const.ID_SPLIT + instanceId;
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();

        if (getResponse.isExists()) {
            return ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_CALLS)).intValue();
        }
        return 0;
    }

    @Override public JsonArray getTpsMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        long timeBucket = startTimeBucket;
        do {
            String id = timeBucket + Const.ID_SPLIT + instanceId;
            prepareMultiGet.add(InstPerformanceTable.TABLE, InstPerformanceTable.TABLE_TYPE, id);
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray metrics = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                metrics.add(((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_CALLS)).intValue());
            } else {
                metrics.add(0);
            }
        }
        return metrics;
    }

    @Override public int getRespTimeMetric(int instanceId, long timeBucket) {
        String id = timeBucket + Const.ID_SPLIT + instanceId;
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();

        if (getResponse.isExists()) {
            int callTimes = ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_CALLS)).intValue();
            int costTotal = ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL)).intValue();
            return costTotal / callTimes;
        }
        return 0;
    }

    @Override public JsonArray getRespTimeMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        int i = 0;
        long timeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), startTimeBucket, i);
            String id = timeBucket + Const.ID_SPLIT + instanceId;
            prepareMultiGet.add(InstPerformanceTable.TABLE, InstPerformanceTable.TABLE_TYPE, id);
            i++;
        }
        while (timeBucket <= endTimeBucket);

        JsonArray metrics = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                int callTimes = ((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_CALLS)).intValue();
                int costTotal = ((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL)).intValue();
                metrics.add(costTotal / callTimes);
            } else {
                metrics.add(0);
            }
        }
        return metrics;
    }
}
