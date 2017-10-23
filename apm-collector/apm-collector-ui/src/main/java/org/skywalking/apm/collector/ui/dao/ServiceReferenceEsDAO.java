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

package org.skywalking.apm.collector.ui.dao;

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
import org.skywalking.apm.collector.cache.ServiceNameCache;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceEsDAO extends EsDAO implements IServiceReferenceDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsDAO.class);

    @Override public Map<String, JsonObject> load(int entryServiceId, long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceReferenceTable.TABLE);
        searchRequestBuilder.setTypes(ServiceReferenceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));

        String entryServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(entryServiceId));
        BoolQueryBuilder entryBoolQuery = QueryBuilders.boolQuery();
        entryBoolQuery.should().add(QueryBuilders.matchQuery(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, entryServiceId));
        entryBoolQuery.should().add(QueryBuilders.matchQuery(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, entryServiceName));
        boolQuery.must(entryBoolQuery);

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        return load(searchRequestBuilder);
    }

    private Map<String, JsonObject> load(SearchRequestBuilder searchRequestBuilder) {
        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S1_LTE).field(ServiceReferenceTable.COLUMN_S1_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S3_LTE).field(ServiceReferenceTable.COLUMN_S3_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_LTE).field(ServiceReferenceTable.COLUMN_S5_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_GT).field(ServiceReferenceTable.COLUMN_S5_GT))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_ERROR).field(ServiceReferenceTable.COLUMN_ERROR))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_SUMMARY).field(ServiceReferenceTable.COLUMN_SUMMARY))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_COST_SUMMARY).field(ServiceReferenceTable.COLUMN_COST_SUMMARY))));

        Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

        SearchResponse searchResponse = searchRequestBuilder.get();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID);
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
        Terms behindServiceIdTerms = frontServiceBucket.getAggregations().get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID);
        for (Terms.Bucket behindServiceIdBucket : behindServiceIdTerms.getBuckets()) {
            int behindServiceId = behindServiceIdBucket.getKeyAsNumber().intValue();
            if (behindServiceId != 0) {
                Sum s1LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S1_LTE);
                Sum s3LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S3_LTE);
                Sum s5LteSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S5_LTE);
                Sum s5GtSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S5_GT);
                Sum error = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_ERROR);
                Sum summary = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_SUMMARY);
                Sum costSum = behindServiceIdBucket.getAggregations().get(ServiceReferenceTable.COLUMN_COST_SUMMARY);

                String frontServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(frontServiceId));
                String behindServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(behindServiceId));

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME), frontServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME), behindServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE), (long)s1LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE), (long)s3LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE), (long)s5LteSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT), (long)s5GtSum.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR), (long)error.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY), (long)summary.getValue());
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY), (long)costSum.getValue());

                String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMap.put(id, serviceReference);
            }
        }
    }
}
