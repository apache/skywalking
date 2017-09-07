package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import java.util.LinkedList;
import java.util.List;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.instance.InstPerformanceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class InstPerformanceEsDAO extends EsDAO implements IInstPerformanceDAO {

    @Override public List<InstPerformance> getMultiple(long timeBucket, int applicationId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstPerformanceTable.TABLE);
        searchRequestBuilder.setTypes(InstPerformanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        MatchQueryBuilder matchApplicationId = QueryBuilders.matchQuery(InstPerformanceTable.COLUMN_APPLICATION_ID, applicationId);
        MatchQueryBuilder matchTimeBucket = QueryBuilders.matchQuery(InstPerformanceTable.COLUMN_5S_TIME_BUCKET, timeBucket);

        boolQuery.must().add(matchApplicationId);
        boolQuery.must().add(matchTimeBucket);

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(InstPerformanceTable.COLUMN_INSTANCE_ID).field(InstPerformanceTable.COLUMN_INSTANCE_ID)
                .subAggregation(
                    AggregationBuilders.terms(InstPerformanceTable.COLUMN_5S_TIME_BUCKET).field(InstPerformanceTable.COLUMN_5S_TIME_BUCKET)
                        .subAggregation(AggregationBuilders.sum(InstPerformanceTable.COLUMN_CALL_TIMES).field(InstPerformanceTable.COLUMN_CALL_TIMES))
                        .subAggregation(AggregationBuilders.sum(InstPerformanceTable.COLUMN_COST_TOTAL).field(InstPerformanceTable.COLUMN_COST_TOTAL))));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms instanceTerms = searchResponse.getAggregations().get(InstPerformanceTable.COLUMN_INSTANCE_ID);
        List<InstPerformance> instPerformances = new LinkedList<>();
        for (Terms.Bucket instanceBucket : instanceTerms.getBuckets()) {
            int instanceId = instanceBucket.getKeyAsNumber().intValue();
            Terms timeBucketTerms = instanceBucket.getAggregations().get(InstPerformanceTable.COLUMN_5S_TIME_BUCKET);
            for (Terms.Bucket timeBucketBucket : timeBucketTerms.getBuckets()) {
                long count = timeBucketBucket.getDocCount();
                Sum sumCallTimes = timeBucketBucket.getAggregations().get(InstPerformanceTable.COLUMN_CALL_TIMES);
                Sum sumCostTotal = timeBucketBucket.getAggregations().get(InstPerformanceTable.COLUMN_COST_TOTAL);
                int avgCallTimes = (int)(sumCallTimes.getValue() / count);
                int avgCost = (int)(sumCostTotal.getValue() / count);
                instPerformances.add(new InstPerformance(instanceId, avgCallTimes, avgCost));
            }
        }

        return instPerformances;
    }

    @Override public int getTpsMetric(int instanceId, long timeBucket) {
        String id = timeBucket + Const.ID_SPLIT + instanceId;
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();

        if (getResponse.isExists()) {
            return ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES)).intValue();
        }
        return 0;
    }

    @Override public JsonArray getTpsMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        long timeBucket = startTimeBucket;
        do {
            String id = timeBucket + Const.ID_SPLIT + instanceId;
            prepareMultiGet.add(InstPerformanceTable.TABLE, InstPerformanceTable.TABLE_TYPE, id);
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray metrics = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                metrics.add(((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES)).intValue());
            } else {
                metrics.add(0);
            }
        }
        return metrics;
    }

    @Override public int getRespTimeMetric(int instanceId, long timeBucket) {
        String id = timeBucket + Const.ID_SPLIT + instanceId;
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();

        if (getResponse.isExists()) {
            int callTimes = ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES)).intValue();
            int costTotal = ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL)).intValue();
            return costTotal / callTimes;
        }
        return 0;
    }

    @Override public JsonArray getRespTimeMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        int i = 0;
        long timeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), startTimeBucket, i);
            String id = timeBucket + Const.ID_SPLIT + instanceId;
            prepareMultiGet.add(InstPerformanceTable.TABLE, InstPerformanceTable.TABLE_TYPE, id);
            i++;
        }
        while (timeBucket <= endTimeBucket);

        JsonArray metrics = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                int callTimes = ((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES)).intValue();
                int costTotal = ((Number)response.getResponse().getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL)).intValue();
                metrics.add(costTotal / callTimes);
            } else {
                metrics.add(0);
            }
        }
        return metrics;
    }
}
