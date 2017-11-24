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

package org.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceEsUIDAO extends EsDAO implements IServiceReferenceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsUIDAO.class);

    public ServiceReferenceEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Map<String, JsonObject> load(int entryServiceId, long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceReferenceMetricTable.TABLE);
        searchRequestBuilder.setTypes(ServiceReferenceMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, entryServiceId));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        return load(searchRequestBuilder);
    }

    private Map<String, JsonObject> load(SearchRequestBuilder searchRequestBuilder) {
        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_S1_LTE).field(ServiceReferenceMetricTable.COLUMN_S1_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_S3_LTE).field(ServiceReferenceMetricTable.COLUMN_S3_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_S5_LTE).field(ServiceReferenceMetricTable.COLUMN_S5_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_S5_GT).field(ServiceReferenceMetricTable.COLUMN_S5_GT))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_ERROR).field(ServiceReferenceMetricTable.COLUMN_ERROR))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_SUMMARY).field(ServiceReferenceMetricTable.COLUMN_SUMMARY))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY).field(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY))));

        Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

        SearchResponse searchResponse = searchRequestBuilder.get();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID);
        for (Terms.Bucket frontServiceBucket : frontServiceIdTerms.getBuckets()) {
            int frontServiceId = frontServiceBucket.getKeyAsNumber().intValue();
            if (frontServiceId != 0) {
                parseSubAggregate(serviceReferenceMap, frontServiceBucket, frontServiceId);
            }
        }

        return serviceReferenceMap;
    }

    private void parseSubAggregate(Map<String, JsonObject> serviceReferenceMap,
        Terms.Bucket frontServiceBucket,
        int frontServiceId) {
        Terms behindServiceIdTerms = frontServiceBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID);
        for (Terms.Bucket behindServiceIdBucket : behindServiceIdTerms.getBuckets()) {
            int behindServiceId = behindServiceIdBucket.getKeyAsNumber().intValue();
            if (behindServiceId != 0) {
                Sum s1LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_S1_LTE);
                Sum s3LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_S3_LTE);
                Sum s5LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_S5_LTE);
                Sum s5GtSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_S5_GT);
                Sum error = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_ERROR);
                Sum summary = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_SUMMARY);
                Sum costSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY);

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_S1_LTE), (long)s1LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_S3_LTE), (long)s3LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_S5_LTE), (long)s5LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_S5_GT), (long)s5GtSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_ERROR), (long)error.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_SUMMARY), (long)summary.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY), (long)costSum.getValue());

                String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMap.put(id, serviceReference);
            }
        }
    }
}
