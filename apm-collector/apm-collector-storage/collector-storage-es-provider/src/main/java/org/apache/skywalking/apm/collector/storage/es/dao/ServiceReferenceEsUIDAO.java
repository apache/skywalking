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

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
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
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM).field(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM))));

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
                Sum calls = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
                Sum errorCalls = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
                Sum durationSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
                Sum errorDurationSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS), (long)calls.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS), (long)errorCalls.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM), (long)durationSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM), (long)errorDurationSum.getValue());

                String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMap.put(id, serviceReference);
            }
        }
    }
}
