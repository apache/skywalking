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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceNode;
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
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricEsUIDAO extends EsDAO implements IServiceMetricUIDAO {

    public ServiceMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Integer> getServiceResponseTimeTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet(durationPoints, new ElasticSearchClient.MultiGetRowHandler<DurationPoint>() {
            @Override
            public void accept(DurationPoint durationPoint) {
                String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
                add(tableName, ServiceMetricTable.TABLE_TYPE, id);
            }
        });

        List<Integer> trends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long calls = ((Number)response.getResponse().getSource().get(ServiceMetricTable.TRANSACTION_CALLS.getName())).longValue();
                long durationSum = ((Number)response.getResponse().getSource().get(ServiceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue();
                trends.add((int)(durationSum / calls));
            } else {
                trends.add(0);
            }
        }
        return trends;
    }

    @Override
    public List<Integer> getServiceThroughputTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet(durationPoints, new ElasticSearchClient.MultiGetRowHandler<DurationPoint>() {
            @Override
            public void accept(DurationPoint durationPoint) {
                String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
                add(tableName, ServiceMetricTable.TABLE_TYPE, id);
            }
        });

        List<Integer> trends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();

        int index = 0;
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long calls = ((Number)response.getResponse().getSource().get(ServiceMetricTable.TRANSACTION_CALLS.getName())).longValue();
                long minutesBetween = durationPoints.get(index).getMinutesBetween();
                trends.add((int)(calls / minutesBetween));
            } else {
                trends.add(0);
            }
            index++;
        }
        return trends;
    }

    @Override
    public List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet(durationPoints, new ElasticSearchClient.MultiGetRowHandler<DurationPoint>() {
            @Override
            public void accept(DurationPoint durationPoint) {
                String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
                add(tableName, ServiceMetricTable.TABLE_TYPE, id);
            }
        });

        List<Integer> trends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long calls = ((Number)response.getResponse().getSource().get(ServiceMetricTable.TRANSACTION_CALLS.getName())).longValue();
                long errorCalls = ((Number)response.getResponse().getSource().get(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue();
                trends.add((int)(((calls - errorCalls) / calls)) * 10000);
            } else {
                trends.add(10000);
            }
        }
        return trends;
    }

    @Override
    public List<Node> getServicesMetric(Step step, long startTimeBucket, long endTimeBucket, MetricSource metricSource,
        Collection<Integer> serviceIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceMetricTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termsQuery(ServiceMetricTable.SERVICE_ID.getName(), serviceIds));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceMetricTable.SOURCE_VALUE.getName(), metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ServiceMetricTable.SERVICE_ID.getName()).field(ServiceMetricTable.SERVICE_ID.getName()).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceMetricTable.TRANSACTION_CALLS.getName()).field(ServiceMetricTable.TRANSACTION_CALLS.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName()).field(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName()));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Node> nodes = new LinkedList<>();
        Terms serviceIdTerms = searchResponse.getAggregations().get(ServiceMetricTable.SERVICE_ID.getName());
        serviceIdTerms.getBuckets().forEach(serviceIdBucket -> {
            int serviceId = serviceIdBucket.getKeyAsNumber().intValue();

            Sum callsSum = serviceIdBucket.getAggregations().get(ServiceMetricTable.TRANSACTION_CALLS.getName());
            Sum errorCallsSum = serviceIdBucket.getAggregations().get(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName());

            ServiceNode serviceNode = new ServiceNode();
            serviceNode.setId(serviceId);
            serviceNode.setCalls((long)callsSum.getValue());
            serviceNode.setSla((int)(((callsSum.getValue() - errorCallsSum.getValue()) / callsSum.getValue()) * 10000));
            nodes.add(serviceNode);
        });
        return nodes;
    }

    @Override
    public List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        Integer topN, MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceMetricTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        if (applicationId != 0) {
            boolQuery.must().add(QueryBuilders.termQuery(ServiceMetricTable.APPLICATION_ID.getName(), applicationId));
        }
        boolQuery.must().add(QueryBuilders.termQuery(ServiceMetricTable.SOURCE_VALUE.getName(), metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(topN * 60);
        searchRequestBuilder.addSort(SortBuilders.fieldSort(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName()).order(SortOrder.DESC));
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        Set<Integer> serviceIds = new HashSet<>();
        List<ServiceMetric> serviceMetrics = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            int serviceId = ((Number)searchHit.getSource().get(ServiceMetricTable.SERVICE_ID.getName())).intValue();
            if (!serviceIds.contains(serviceId)) {
                ServiceMetric serviceMetric = new ServiceMetric();
                serviceMetric.getService().setId(serviceId);
                serviceMetric.getService().setApplicationId(serviceId);
                serviceMetric.setCalls(((Number)searchHit.getSource().get(ServiceMetricTable.TRANSACTION_CALLS.getName())).longValue());
                serviceMetric.setAvgResponseTime(((Number)searchHit.getSource().get(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).intValue());
                serviceMetrics.add(serviceMetric);

                serviceIds.add(serviceId);
            }
            if (topN == serviceIds.size()) {
                break;
            }
        }

        return serviceMetrics;
    }
}
