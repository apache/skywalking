package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;
import org.skywalking.apm.collector.storage.define.jvm.GCMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GCMetricEsDAO extends EsDAO implements IGCMetricDAO {

    private final Logger logger = LoggerFactory.getLogger(GCMetricEsDAO.class);

    @Override public GCCount getGCCount(long s5TimeBucket, int instanceId) {
        logger.debug("get gc count, s5TimeBucket: {}, instanceId: {}", s5TimeBucket, instanceId);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GCMetricTable.TABLE);
        searchRequestBuilder.setTypes(GCMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        MatchQueryBuilder matchApplicationId = QueryBuilders.matchQuery(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, instanceId);
        MatchQueryBuilder matchTimeBucket = QueryBuilders.matchQuery(GCMetricTable.COLUMN_5S_TIME_BUCKET, s5TimeBucket);

        boolQuery.must().add(matchApplicationId);
        boolQuery.must().add(matchTimeBucket);

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(GCMetricTable.COLUMN_PHRASE).field(GCMetricTable.COLUMN_PHRASE)
                .subAggregation(AggregationBuilders.sum(GCMetricTable.COLUMN_COUNT).field(GCMetricTable.COLUMN_COUNT)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        GCCount gcCount = new GCCount();
        Terms phraseAggregation = searchResponse.getAggregations().get(GCMetricTable.COLUMN_PHRASE);
        for (Terms.Bucket phraseBucket : phraseAggregation.getBuckets()) {
            int phrase = phraseBucket.getKeyAsNumber().intValue();
            Sum sumAggregation = phraseBucket.getAggregations().get(GCMetricTable.COLUMN_COUNT);
            int count = (int)sumAggregation.getValue();

            if (phrase == GCPhrase.NEW_VALUE) {
                gcCount.setYoung(count);
            } else if (phrase == GCPhrase.OLD_VALUE) {
                gcCount.setOld(count);
            }
        }

        return gcCount;
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket) {
        JsonObject response = new JsonObject();

        String youngId = timeBucket + Const.ID_SPLIT + GCPhrase.NEW_VALUE + instanceId;
        GetResponse youngResponse = getClient().prepareGet(GCMetricTable.TABLE, youngId).get();
        if (youngResponse.isExists()) {
            response.addProperty("ygc", ((Number)youngResponse.getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
        }

        String oldId = timeBucket + Const.ID_SPLIT + GCPhrase.OLD_VALUE + instanceId;
        GetResponse oldResponse = getClient().prepareGet(GCMetricTable.TABLE, oldId).get();
        if (oldResponse.isExists()) {
            response.addProperty("ogc", ((Number)oldResponse.getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
        }

        return response;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        JsonObject response = new JsonObject();

        MultiGetRequestBuilder youngPrepareMultiGet = getClient().prepareMultiGet();
        int i = 0;
        do {
            String youngId = (startTimeBucket + i) + Const.ID_SPLIT + GCPhrase.NEW_VALUE + instanceId;
            youngPrepareMultiGet.add(CpuMetricTable.TABLE, CpuMetricTable.TABLE_TYPE, youngId);
            i++;
        }
        while (startTimeBucket + i <= endTimeBucket);

        JsonArray youngArray = new JsonArray();
        MultiGetResponse multiGetResponse = youngPrepareMultiGet.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse.getResponses()) {
            if (itemResponse.getResponse().isExists()) {
                youngArray.add(((Number)itemResponse.getResponse().getSource().get(CpuMetricTable.COLUMN_USAGE_PERCENT)).intValue());
            } else {
                youngArray.add(0);
            }
        }
        response.add("ygc", youngArray);

        MultiGetRequestBuilder oldPrepareMultiGet = getClient().prepareMultiGet();
        i = 0;
        do {
            String oldId = (startTimeBucket + i) + Const.ID_SPLIT + GCPhrase.OLD_VALUE + instanceId;
            oldPrepareMultiGet.add(CpuMetricTable.TABLE, CpuMetricTable.TABLE_TYPE, oldId);
            i++;
        }
        while (startTimeBucket + i <= endTimeBucket);

        JsonArray oldArray = new JsonArray();

        multiGetResponse = oldPrepareMultiGet.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse.getResponses()) {
            if (itemResponse.getResponse().isExists()) {
                oldArray.add(((Number)itemResponse.getResponse().getSource().get(CpuMetricTable.COLUMN_USAGE_PERCENT)).intValue());
            } else {
                oldArray.add(0);
            }
        }
        response.add("ogc", oldArray);

        return response;
    }
}
