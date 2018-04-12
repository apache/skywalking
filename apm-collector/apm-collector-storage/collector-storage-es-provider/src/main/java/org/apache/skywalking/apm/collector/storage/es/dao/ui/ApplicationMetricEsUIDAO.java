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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.ApplicationTPS;
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
public class ApplicationMetricEsUIDAO extends EsDAO implements IApplicationMetricUIDAO {

    public ApplicationMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<ApplicationTPS> getTopNApplicationThroughput(Step step, long startTimeBucket, long endTimeBucket,
        int betweenSecond, int topN, MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationMetricTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationMetricTable.SOURCE_VALUE.getName(), metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationMetricTable.APPLICATION_ID.getName()).field(ApplicationMetricTable.APPLICATION_ID.getName()).size(2000);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TRANSACTION_CALLS.getName()).field(ApplicationMetricTable.TRANSACTION_CALLS.getName()));
        searchRequestBuilder.addAggregation(aggregationBuilder);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<ApplicationTPS> applicationTPSs = new LinkedList<>();
        Terms applicationIdTerms = searchResponse.getAggregations().get(ApplicationMetricTable.APPLICATION_ID.getName());
        applicationIdTerms.getBuckets().forEach(applicationIdTerm -> {
            int applicationId = applicationIdTerm.getKeyAsNumber().intValue();
            Sum callSum = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TRANSACTION_CALLS.getName());
            long calls = (long)callSum.getValue();
            int callsPerSec = (int)(betweenSecond == 0 ? 0 : calls / betweenSecond);

            ApplicationTPS applicationTPS = new ApplicationTPS();
            applicationTPS.setApplicationId(applicationId);
            applicationTPS.setCallsPerSec(callsPerSec);
            applicationTPSs.add(applicationTPS);
        });

        applicationTPSs.sort((first, second) -> first.getCallsPerSec() > second.getCallsPerSec() ? -1 : 1);

        if (applicationTPSs.size() <= topN) {
            return applicationTPSs;
        } else {
            List<ApplicationTPS> newCollection = new LinkedList<>();
            for (int i = 0; i < topN; i++) {
                newCollection.add(applicationTPSs.get(i));
            }
            return newCollection;
        }
    }

    @Override
    public List<ApplicationMetric> getApplications(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationMetricTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationMetricTable.SOURCE_VALUE.getName(), metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ApplicationMetricTable.APPLICATION_ID.getName()).field(ApplicationMetricTable.APPLICATION_ID.getName()).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TRANSACTION_CALLS.getName()).field(ApplicationMetricTable.TRANSACTION_CALLS.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName()).field(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName()).field(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()).field(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.SATISFIED_COUNT.getName()).field(ApplicationMetricTable.SATISFIED_COUNT.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.TOLERATING_COUNT.getName()).field(ApplicationMetricTable.TOLERATING_COUNT.getName()));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationMetricTable.FRUSTRATED_COUNT.getName()).field(ApplicationMetricTable.FRUSTRATED_COUNT.getName()));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<ApplicationMetric> applicationMetrics = new LinkedList<>();
        Terms applicationIdTerms = searchResponse.getAggregations().get(ApplicationMetricTable.APPLICATION_ID.getName());
        applicationIdTerms.getBuckets().forEach(applicationIdTerm -> {
            int applicationId = applicationIdTerm.getKeyAsNumber().intValue();

            Sum calls = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TRANSACTION_CALLS.getName());
            Sum errorCalls = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName());
            Sum durations = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName());
            Sum errorDurations = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());
            Sum satisfiedCount = applicationIdTerm.getAggregations().get(ApplicationMetricTable.SATISFIED_COUNT.getName());
            Sum toleratingCount = applicationIdTerm.getAggregations().get(ApplicationMetricTable.TOLERATING_COUNT.getName());
            Sum frustratedCount = applicationIdTerm.getAggregations().get(ApplicationMetricTable.FRUSTRATED_COUNT.getName());

            ApplicationMetric applicationMetric = new ApplicationMetric();
            applicationMetric.setId(applicationId);
            applicationMetric.setCalls((long)calls.getValue());
            applicationMetric.setErrorCalls((long)errorCalls.getValue());
            applicationMetric.setDurations((long)durations.getValue());
            applicationMetric.setErrorDurations((long)errorDurations.getValue());
            applicationMetric.setSatisfiedCount((long)satisfiedCount.getValue());
            applicationMetric.setToleratingCount((long)toleratingCount.getValue());
            applicationMetric.setToleratingCount((long)frustratedCount.getValue());
            applicationMetrics.add(applicationMetric);
        });
        return applicationMetrics;
    }
}
