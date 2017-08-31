package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.ui.cache.ServiceIdCache;
import org.skywalking.apm.collector.ui.cache.ServiceNameCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceReferenceEsDAO extends EsDAO implements IServiceReferenceDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsDAO.class);

    @Override public JsonArray load(int entryServiceId, long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceReferenceTable.TABLE);
        searchRequestBuilder.setTypes(ServiceReferenceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.should().add(QueryBuilders.matchQuery(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, entryServiceId));
        boolQuery.should().add(QueryBuilders.matchQuery(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, ServiceNameCache.getForUI(entryServiceId)));
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceReferenceTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID).field(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_SUMMARY).field(ServiceReferenceTable.COLUMN_SUMMARY)))
            .subAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME).field(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S1_LTE).field(ServiceReferenceTable.COLUMN_S1_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S3_LTE).field(ServiceReferenceTable.COLUMN_S3_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_LTE).field(ServiceReferenceTable.COLUMN_S5_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_GT).field(ServiceReferenceTable.COLUMN_S5_GT))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_ERROR).field(ServiceReferenceTable.COLUMN_ERROR))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_SUMMARY).field(ServiceReferenceTable.COLUMN_SUMMARY))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_COST_SUMMARY).field(ServiceReferenceTable.COLUMN_COST_SUMMARY))));

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME).field(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME).size(100)
            .subAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).field(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_SUMMARY).field(ServiceReferenceTable.COLUMN_SUMMARY)))
            .subAggregation(AggregationBuilders.terms(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME).field(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME).size(100)
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S1_LTE).field(ServiceReferenceTable.COLUMN_S1_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S3_LTE).field(ServiceReferenceTable.COLUMN_S3_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_LTE).field(ServiceReferenceTable.COLUMN_S5_LTE))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_S5_GT).field(ServiceReferenceTable.COLUMN_S5_GT))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_ERROR).field(ServiceReferenceTable.COLUMN_ERROR))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_SUMMARY).field(ServiceReferenceTable.COLUMN_SUMMARY))
                .subAggregation(AggregationBuilders.sum(ServiceReferenceTable.COLUMN_COST_SUMMARY).field(ServiceReferenceTable.COLUMN_COST_SUMMARY))));

        JsonArray serviceReferenceArray = new JsonArray();
        SearchResponse searchResponse = searchRequestBuilder.get();
        Terms frontServiceIdTerms = searchResponse.getAggregations().get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID);
        for (Terms.Bucket frontServiceBucket : frontServiceIdTerms.getBuckets()) {
            int frontServiceId = frontServiceBucket.getKeyAsNumber().intValue();
            if (frontServiceId != 0) {
                parseSubAggregate(serviceReferenceArray, frontServiceBucket, frontServiceId);
            }
        }

        Terms frontServiceNameTerms = searchResponse.getAggregations().get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME);
        for (Terms.Bucket frontServiceBucket : frontServiceNameTerms.getBuckets()) {
            String frontServiceName = frontServiceBucket.getKeyAsString();
            if (StringUtils.isNotEmpty(frontServiceName)) {
                String[] serviceNames = frontServiceName.split(Const.ID_SPLIT);
                int frontServiceId = ServiceIdCache.getForUI(Integer.parseInt(serviceNames[0]), serviceNames[1]);
                parseSubAggregate(serviceReferenceArray, frontServiceBucket, frontServiceId);
            }
        }
        return serviceReferenceArray;
    }

    private void parseSubAggregate(JsonArray serviceReferenceArray, Terms.Bucket frontServiceBucket,
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

                String frontServiceName = ServiceNameCache.getForUI(frontServiceId);
                String behindServiceName = ServiceNameCache.getForUI(behindServiceId);

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
                serviceReferenceArray.add(serviceReference);
            }
        }

        Terms behindServiceNameTerms = frontServiceBucket.getAggregations().get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME);
        for (Terms.Bucket behindServiceNameBucket : behindServiceNameTerms.getBuckets()) {
            String behindServiceName = behindServiceNameBucket.getKeyAsString();
            if (StringUtils.isNotEmpty(behindServiceName)) {
                Sum s1LteSum = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S1_LTE);
                Sum s3LteSum = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S3_LTE);
                Sum s5LteSum = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S5_LTE);
                Sum s5GtSum = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_S5_GT);
                Sum error = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_ERROR);
                Sum summary = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_SUMMARY);
                Sum costSum = behindServiceNameBucket.getAggregations().get(ServiceReferenceTable.COLUMN_COST_SUMMARY);

                String frontServiceName = ServiceNameCache.getForUI(frontServiceId);
                String[] serviceNames = behindServiceName.split(Const.ID_SPLIT);
                int behindServiceId = ServiceIdCache.getForUI(Integer.parseInt(serviceNames[0]), serviceNames[1]);
                behindServiceName = serviceNames[1];

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
                serviceReferenceArray.add(serviceReference);
            }
        }
    }
}
