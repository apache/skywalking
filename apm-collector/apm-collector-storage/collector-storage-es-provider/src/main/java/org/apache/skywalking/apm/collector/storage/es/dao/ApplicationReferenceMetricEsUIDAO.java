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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
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
public class ApplicationReferenceMetricEsUIDAO extends EsDAO implements IApplicationReferenceMetricUIDAO {

    public ApplicationReferenceMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<Call> getFrontApplications(Step step, int applicationId, long startTime, long endTime,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> nodes = new LinkedList<>();
        Terms frontApplicationIdTerms = searchResponse.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID);
        for (Terms.Bucket frontApplicationIdBucket : frontApplicationIdTerms.getBuckets()) {
            int frontApplicationId = frontApplicationIdBucket.getKeyAsNumber().intValue();
            Sum calls = frontApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum responseTimes = frontApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

            Call call = new Call();
            call.setSource(frontApplicationId);
            call.setTarget(applicationId);
            call.setCalls((int)calls.getValue());
            call.setResponseTimes((int)responseTimes.getValue());
            nodes.add(call);
        }

        return nodes;
    }

    @Override public List<Call> getBehindApplications(Step step, int applicationId, long startTime, long endTime,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<Call> nodes = new LinkedList<>();
        Terms behindApplicationIdTerms = searchResponse.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
        for (Terms.Bucket behindApplicationIdBucket : behindApplicationIdTerms.getBuckets()) {
            int behindApplicationId = behindApplicationIdBucket.getKeyAsNumber().intValue();
            Sum calls = behindApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum responseTimes = behindApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);

            Call call = new Call();
            call.setTarget(behindApplicationId);
            call.setSource(applicationId);
            call.setCalls((int)calls.getValue());
            call.setResponseTimes((int)responseTimes.getValue());
            nodes.add(call);
        }

        return nodes;
    }
}
