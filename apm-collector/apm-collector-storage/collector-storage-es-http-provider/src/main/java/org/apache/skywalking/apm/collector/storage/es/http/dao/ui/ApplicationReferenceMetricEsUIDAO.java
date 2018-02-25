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

import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.SumAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation.Entry;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricEsUIDAO extends EsHttpDAO implements IApplicationReferenceMetricUIDAO {

    public ApplicationReferenceMetricEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<Call> getFrontApplications(Step step, int applicationId, long startTime, long endTime,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
//        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        


        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

//        searchRequestBuilder.setQuery(boolQuery);
//        searchRequestBuilder.setSize(0);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

//        searchRequestBuilder.addAggregation(aggregationBuilder);
        searchSourceBuilder.aggregation(aggregationBuilder);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(tableName).build();
        
        SearchResult result = getClient().execute(search);
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> nodes = new LinkedList<>();
        TermsAggregation frontApplicationIdTerms = result.getAggregations().getTermsAggregation(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID);
        for (Entry frontApplicationIdBucket : frontApplicationIdTerms.getBuckets()) {
            int frontApplicationId = Integer.parseInt(frontApplicationIdBucket.getKeyAsString());
            SumAggregation calls = frontApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            SumAggregation responseTimes = frontApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
            
            Call call = new Call();
            call.setSource(frontApplicationId);
            call.setTarget(applicationId);
            call.setCalls(calls.getSum().intValue());
            call.setResponseTimes(responseTimes.getSum().intValue());
            nodes.add(call);
        }

        return nodes;
    }

    @Override public List<Call> getBehindApplications(Step step, int applicationId, long startTime, long endTime,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
//        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

//        searchRequestBuilder.setQuery(boolQuery);
//        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

//        searchRequestBuilder.addAggregation(aggregationBuilder);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(aggregationBuilder);
        searchSourceBuilder.size(0);
        searchSourceBuilder.query(boolQuery);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(tableName).build();
        
        SearchResult searchResponse = getClient().execute(search);

        List<Call> nodes = new LinkedList<>();
        TermsAggregation behindApplicationIdTerms = searchResponse.getAggregations().getTermsAggregation(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
        for (Entry behindApplicationIdBucket : behindApplicationIdTerms.getBuckets()) {
            int behindApplicationId = Integer.parseInt(behindApplicationIdBucket.getKeyAsString());
            SumAggregation calls = behindApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            SumAggregation responseTimes = behindApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

            Call call = new Call();
            call.setTarget(behindApplicationId);
            call.setSource(applicationId);
            call.setCalls(calls.getSum().intValue());
            call.setResponseTimes(responseTimes.getSum().intValue());
            nodes.add(call);
        }

        return nodes;
    }

    @Override public List<Call> getApplications(Step step, long startTime, long endTime, MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
//        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));
//
//        searchRequestBuilder.setQuery(boolQuery);
//        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

//        searchRequestBuilder.addAggregation(aggregationBuilder);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(aggregationBuilder);
        searchSourceBuilder.size(0);
        searchSourceBuilder.query(boolQuery);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(tableName).build();
        
        SearchResult searchResponse = getClient().execute(search);
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> nodes = new LinkedList<>();
        TermsAggregation frontApplicationIdTerms = searchResponse.getAggregations().getTermsAggregation(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID);
        for (Entry frontApplicationIdBucket : frontApplicationIdTerms.getBuckets()) {
            int frontApplicationId = Integer.parseInt(frontApplicationIdBucket.getKeyAsString());

            TermsAggregation behindApplicationIdTerms = frontApplicationIdBucket.getTermsAggregation(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
            for (Entry behindApplicationIdBucket : behindApplicationIdTerms.getBuckets()) {
                int behindApplicationId = Integer.parseInt(behindApplicationIdBucket.getKeyAsString());

                SumAggregation calls = behindApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
                SumAggregation responseTimes = behindApplicationIdBucket.getSumAggregation(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

                Call call = new Call();
                call.setResponseTimes(responseTimes.getSum().intValue());
                call.setSource(frontApplicationId);
                call.setTarget(behindApplicationId);
                call.setCalls(calls.getSum().intValue());
                nodes.add(call);
            }
        }

        return nodes;
    }
}
