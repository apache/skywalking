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
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
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

    @Override public List<ApplicationReferenceMetric> getReferences(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource, Integer... applicationIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, metricSource.getValue()));

        if (CollectionUtils.isNotEmpty(applicationIds)) {
            BoolQueryBuilder applicationBoolQuery = QueryBuilders.boolQuery();
            int[] ids = new int[applicationIds.length];
            for (int i = 0; i < applicationIds.length; i++) {
                ids[i] = applicationIds[i];
            }

            applicationBoolQuery.should().add(QueryBuilders.termsQuery(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, ids));
            applicationBoolQuery.should().add(QueryBuilders.termsQuery(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, ids));
            boolQuery.must().add(applicationBoolQuery);
        }

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        return buildMetrics(searchRequestBuilder);
    }

    private List<ApplicationReferenceMetric> buildMetrics(SearchRequestBuilder searchRequestBuilder) {
        TermsAggregationBuilder frontAggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID).size(100);
        TermsAggregationBuilder behindAggregationBuilder = AggregationBuilders.terms(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).field(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID).size(100);
        frontAggregationBuilder.subAggregation(behindAggregationBuilder);

        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        behindAggregationBuilder.subAggregation(AggregationBuilders.sum(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM).field(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

        searchRequestBuilder.addAggregation(frontAggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        List<ApplicationReferenceMetric> referenceMetrics = new LinkedList<>();
        Terms sourceApplicationIdTerms = searchResponse.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID);
        for (Terms.Bucket sourceApplicationIdBucket : sourceApplicationIdTerms.getBuckets()) {
            int sourceApplicationId = sourceApplicationIdBucket.getKeyAsNumber().intValue();

            Terms targetApplicationIdTerms = sourceApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
            for (Terms.Bucket targetApplicationIdBucket : targetApplicationIdTerms.getBuckets()) {
                int targetApplicationId = targetApplicationIdBucket.getKeyAsNumber().intValue();

                Sum calls = targetApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
                Sum errorCalls = targetApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
                Sum durations = targetApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
                Sum errorDurations = targetApplicationIdBucket.getAggregations().get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);

                ApplicationReferenceMetric referenceMetric = new ApplicationReferenceMetric();
                referenceMetric.setSource(sourceApplicationId);
                referenceMetric.setTarget(targetApplicationId);
                referenceMetric.setCalls((long)calls.getValue());
                referenceMetric.setErrorCalls((long)errorCalls.getValue());
                referenceMetric.setDurations((long)durations.getValue());
                referenceMetric.setErrorDurations((long)errorDurations.getValue());
                referenceMetrics.add(referenceMetric);
            }
        }

        return referenceMetrics;
    }
}
