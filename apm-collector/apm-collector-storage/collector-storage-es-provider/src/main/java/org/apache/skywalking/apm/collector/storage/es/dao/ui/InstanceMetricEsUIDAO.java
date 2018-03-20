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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
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
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricEsUIDAO extends EsDAO implements IInstanceMetricUIDAO {

    public InstanceMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<AppServerInfo> getServerThroughput(int applicationId, Step step, long startTimeBucket,
        long endTimeBucket, int secondBetween, int topN, MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(InstanceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(InstanceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        if (applicationId != 0) {
            boolQuery.must().add(QueryBuilders.termQuery(InstanceMetricTable.COLUMN_APPLICATION_ID, applicationId));
        }
        boolQuery.must().add(QueryBuilders.termQuery(InstanceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(InstanceMetricTable.COLUMN_INSTANCE_ID).field(InstanceMetricTable.COLUMN_INSTANCE_ID).size(2000);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(InstanceMetricTable.COLUMN_TRANSACTION_CALLS).field(InstanceMetricTable.COLUMN_TRANSACTION_CALLS));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<AppServerInfo> appServerInfos = new LinkedList<>();
        Terms instanceIdTerms = searchResponse.getAggregations().get(InstanceMetricTable.COLUMN_INSTANCE_ID);
        instanceIdTerms.getBuckets().forEach(instanceIdTerm -> {
            int instanceId = instanceIdTerm.getKeyAsNumber().intValue();
            Sum callSum = instanceIdTerm.getAggregations().get(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS);
            long calls = (long)callSum.getValue();
            int callsPerSec = (int)(secondBetween == 0 ? 0 : calls / secondBetween);

            AppServerInfo appServerInfo = new AppServerInfo();
            appServerInfo.setId(instanceId);
            appServerInfo.setCallsPerSec(callsPerSec);
            appServerInfos.add(appServerInfo);
        });

        appServerInfos.sort((first, second) -> first.getCallsPerSec() > second.getCallsPerSec() ? -1 : 1);
        if (appServerInfos.size() <= topN) {
            return appServerInfos;
        } else {
            List<AppServerInfo> newCollection = new LinkedList<>();
            for (int i = 0; i < topN; i++) {
                newCollection.add(appServerInfos.get(i));
            }
            return newCollection;
        }
    }

    @Override public List<Integer> getServerTPSTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
        });

        List<Integer> throughputTrend = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();

        for (int i = 0; i < multiGetResponse.getResponses().length; i++) {
            MultiGetItemResponse response = multiGetResponse.getResponses()[i];
            if (response.getResponse().isExists()) {
                long callTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue();
                throughputTrend.add((int)(callTimes / durationPoints.get(i).getSecondsBetween()));
            } else {
                throughputTrend.add(0);
            }
        }
        return throughputTrend;
    }

    @Override public List<Integer> getResponseTimeTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
        });

        List<Integer> responseTimeTrends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long callTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue();
                long errorCallTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue();
                long durationSum = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue();
                long errorDurationSum = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue();
                long correctCallTimes = callTimes - errorCallTimes;
                if (correctCallTimes != 0L) {
                    responseTimeTrends.add((int)((durationSum - errorDurationSum) / correctCallTimes));
                } else {
                    responseTimeTrends.add(0);
                }
            } else {
                responseTimeTrends.add(0);
            }
        }
        return responseTimeTrends;
    }
}
