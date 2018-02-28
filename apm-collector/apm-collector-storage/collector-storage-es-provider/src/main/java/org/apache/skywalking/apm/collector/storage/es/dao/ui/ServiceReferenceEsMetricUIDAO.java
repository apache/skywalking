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

    @Override public List<ServiceReferenceMetric> getFrontServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource,
        int behindServiceId) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, behindServiceId));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<ServiceReferenceMetric> referenceMetrics = new LinkedList<>();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID);
        buildNodeByBehindServiceId(referenceMetrics, frontServiceIdTerms, behindServiceId);

        return referenceMetrics;
    }

    @Override public List<ServiceReferenceMetric> getBehindServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource,
        int frontServiceId) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, frontServiceId));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<ServiceReferenceMetric> referenceMetrics = new LinkedList<>();
        Terms behindServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID);
        buildNodeByFrontServiceId(referenceMetrics, behindServiceIdTerms, frontServiceId);

        return referenceMetrics;
    }

    private void buildNodeByFrontServiceId(List<ServiceReferenceMetric> referenceMetrics, Terms behindServiceIdTerms,
        int frontServiceId) {
        behindServiceIdTerms.getBuckets().forEach(behindServiceIdBucket -> {
            int behindServiceId = behindServiceIdBucket.getKeyAsNumber().intValue();

            Sum callsSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum errorCallsSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
            Sum durationSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
            Sum errorDurationSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);

            ServiceReferenceMetric referenceMetric = new ServiceReferenceMetric();
            referenceMetric.setSource(frontServiceId);
            referenceMetric.setTarget(behindServiceId);
            referenceMetric.setCalls((long)callsSum.getValue());
            referenceMetric.setErrorCalls((long)errorCallsSum.getValue());
            referenceMetric.setDurations((long)durationSum.getValue());
            referenceMetric.setErrorDurations((long)errorDurationSum.getValue());
            referenceMetrics.add(referenceMetric);
        });
    }

    private void buildNodeByBehindServiceId(List<ServiceReferenceMetric> referenceMetrics, Terms frontServiceIdTerms,
        int behindServiceId) {
        frontServiceIdTerms.getBuckets().forEach(frontServiceIdBucket -> {
            int frontServiceId = frontServiceIdBucket.getKeyAsNumber().intValue();

            Sum callsSum = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
            Sum errorCallsSum = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
            Sum durationSum = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
            Sum errorDurationSum = frontServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);

            ServiceReferenceMetric referenceMetric = new ServiceReferenceMetric();
            referenceMetric.setTarget(behindServiceId);
            referenceMetric.setSource(frontServiceId);
            referenceMetric.setCalls((long)callsSum.getValue());
            referenceMetric.setErrorCalls((long)errorCallsSum.getValue());
            referenceMetric.setDurations((long)durationSum.getValue());
            referenceMetric.setErrorDurations((long)errorDurationSum.getValue());
            referenceMetrics.add(referenceMetric);
        });
    }
}
