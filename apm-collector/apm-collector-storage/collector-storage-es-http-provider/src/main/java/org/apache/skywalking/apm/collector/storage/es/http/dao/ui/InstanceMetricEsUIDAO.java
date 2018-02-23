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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.client.JestResult;
import io.searchbox.core.MultiGet;
import io.searchbox.core.Search;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricEsUIDAO extends EsDAO implements IInstanceMetricUIDAO {

    private static final String AVG_TPS = "avg_tps";

    public InstanceMetricEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<AppServerInfo> getServerThroughput(int applicationId, Step step, long start, long end,
            long secondBetween, int topN, MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(InstanceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(InstanceMetricTable.COLUMN_TIME_BUCKET).gte(start).lte(end));
        if (applicationId != 0) {
            boolQuery.must().add(QueryBuilders.termQuery(InstanceMetricTable.COLUMN_APPLICATION_ID, applicationId));
        }
        boolQuery.must().add(QueryBuilders.termQuery(InstanceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(InstanceMetricTable.COLUMN_INSTANCE_ID).field(InstanceMetricTable.COLUMN_INSTANCE_ID).size(topN);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(InstanceMetricTable.COLUMN_TRANSACTION_CALLS).field(InstanceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS).field(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));

        Map<String, String> bucketsPathsMap = new HashMap<>();
        bucketsPathsMap.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, InstanceMetricTable.COLUMN_TRANSACTION_CALLS);
        bucketsPathsMap.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);

        String idOrCode = "(params." + InstanceMetricTable.COLUMN_TRANSACTION_CALLS + " - params." + InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS + ")"
                + " / "
                + "( " + secondBetween + " )";
        Script script = new Script(idOrCode);
        aggregationBuilder.subAggregation(PipelineAggregatorBuilders.bucketScript(AVG_TPS, bucketsPathsMap, script));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<AppServerInfo> appServerInfos = new LinkedList<>();
        Terms serviceIdTerms = searchResponse.getAggregations().get(InstanceMetricTable.COLUMN_INSTANCE_ID);
        serviceIdTerms.getBuckets().forEach(serviceIdTerm -> {
            int instanceId = serviceIdTerm.getKeyAsNumber().intValue();

            AppServerInfo appServerInfo = new AppServerInfo();
            InternalSimpleValue simpleValue = serviceIdTerm.getAggregations().get(AVG_TPS);

            appServerInfo.setId(instanceId);
            appServerInfo.setTps((int)simpleValue.getValue());
            appServerInfos.add(appServerInfo);
        });
        return appServerInfos;
    }

    @Override public List<Integer> getServerTPSTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
//        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);
        
        MultiGet.Builder.ById multiGet = new MultiGet.Builder.ById(tableName,  InstanceMetricTable.TABLE_TYPE);

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
//            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
            multiGet.addId(id);
        });

        List<Integer> throughputTrend = new LinkedList<>();
        
        

        JestResult multiGetResponse =getClient().execute(multiGet.build());

        JsonArray docs = multiGetResponse.getJsonObject().getAsJsonArray("docs");
        for (int i = 0 ; i < docs.size() ; i ++) {
            JsonObject docJson = docs.get(i).getAsJsonObject();
            if (docJson.get("found").getAsBoolean()) {
                JsonObject source = docJson.getAsJsonObject("_source");
                long callTimes = (source.get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).getAsLong();
                throughputTrend.add((int)(callTimes / durationPoints.get(i).getSecondsBetween()));
            } else {
                throughputTrend.add(0);
            }
        }
        return throughputTrend;
    }

    @Override public List<Integer> getResponseTimeTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
//        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);
        List<String> ids = Lists.newArrayList();

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
//            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
            ids.add(id);
        });

        MultiGet mlutiGet = new MultiGet.Builder.ById(tableName, InstanceMetricTable.TABLE_TYPE).addId(ids).build();

        JestResult result =  getClient().execute(mlutiGet);
        List<JsonObject> gets =  result.getSourceAsObjectList(JsonObject.class);

        List<Integer> responseTimeTrends = new LinkedList<>();
//        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (JsonObject response : gets) {
            if (!response.isJsonNull()) {
                long callTimes = (response.get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).getAsLong();
                long errorCallTimes = (response.get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).getAsLong();
                long durationSum = (response.get(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).getAsLong();
                long errorDurationSum = (response.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).getAsLong();
                responseTimeTrends.add((int)((durationSum - errorDurationSum) / (callTimes - errorCallTimes)));
            } else {
                responseTimeTrends.add(0);
            }
        }
        return responseTimeTrends;
    }
}
