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
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
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
public class ServiceReferenceEsMetricUIDAO extends EsDAO implements IServiceReferenceMetricUIDAO {

    public ServiceReferenceEsMetricUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<Call> getFrontServices(Step step, long startTime, long endTime, MetricSource metricSource,
        int behindServiceId) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, behindServiceId));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> calls = new LinkedList<>();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID);
        buildNodeByBehindServiceId(calls, frontServiceIdTerms, behindServiceId);

        return calls;
    }

    @Override public List<Call> getBehindServices(Step step, long startTime, long endTime, MetricSource metricSource,
        int frontServiceId) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, frontServiceId));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> calls = new LinkedList<>();
        Terms behindServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID);
        buildNodeByFrontServiceId(calls, behindServiceIdTerms, frontServiceId);

        return calls;
    }

    @Override public List<Call> getFrontServices(Step step, long startTime, long endTime, MetricSource metricSource,
        List<Integer> behindServiceIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termsQuery(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, behindServiceIds));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        return executeAggregation(searchRequestBuilder);
    }

    @Override public List<Call> getBehindServices(Step step, long startTime, long endTime, MetricSource metricSource,
        List<Integer> frontServiceIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termsQuery(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, frontServiceIds));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        return executeAggregation(searchRequestBuilder);
    }

    private List<Call> executeAggregation(SearchRequestBuilder searchRequestBuilder) {
        TermsAggregationBuilder frontAggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).size(100);
        TermsAggregationBuilder behindAggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).size(100);
        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        frontAggregationBuilder.subAggregation(behindAggregationBuilder);

        searchRequestBuilder.addAggregation(frontAggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> nodes = new LinkedList<>();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID);
        frontServiceIdTerms.getBuckets().forEach(frontServiceIdBucket -> {
            int frontServiceId = frontServiceIdBucket.getKeyAsNumber().intValue();

            Terms behindServiceIdTerms = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID);
            buildNodeByFrontServiceId(nodes, behindServiceIdTerms, frontServiceId);
        });

        return nodes;
    }

    private void buildNodeByFrontServiceId(List<Call> calls, Terms behindServiceIdTerms, int frontServiceId) {
        behindServiceIdTerms.getBuckets().forEach(behindServiceIdBucket -> {
            int behindServiceId = behindServiceIdBucket.getKeyAsNumber().intValue();

            Sum callsSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum responseTimes = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

            Call call = new Call();
            call.setSource(frontServiceId);
            call.setTarget(behindServiceId);
//            call.setCalls((int)callsSum.getValue());
//            call.setResponseTimes((int)responseTimes.getValue());
            calls.add(call);
        });
    }

    private void buildNodeByBehindServiceId(List<Call> calls, Terms frontServiceIdTerms, int behindServiceId) {
        frontServiceIdTerms.getBuckets().forEach(frontServiceIdBucket -> {
            int frontServiceId = frontServiceIdBucket.getKeyAsNumber().intValue();
            Sum callsSum = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum responseTimes = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

            Call call = new Call();
            call.setTarget(behindServiceId);
            call.setSource(frontServiceId);
//            call.setCalls((int)callsSum.getValue());
//            call.setResponseTimes((int)responseTimes.getValue());
            calls.add(call);
        });
    }
}
