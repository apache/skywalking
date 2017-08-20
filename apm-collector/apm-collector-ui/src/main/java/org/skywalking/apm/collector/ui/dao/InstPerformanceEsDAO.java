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
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.instance.InstPerformanceTable;
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class InstPerformanceEsDAO extends EsDAO implements IInstPerformanceDAO {

    @Override public List<InstPerformance> getMultiple(long timestamp, int applicationId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstPerformanceTable.TABLE);
        searchRequestBuilder.setTypes(InstPerformanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        MatchQueryBuilder matchApplicationId = QueryBuilders.matchQuery(InstPerformanceTable.COLUMN_APPLICATION_ID, applicationId);
        MatchQueryBuilder matchTimeBucket = QueryBuilders.matchQuery(InstPerformanceTable.COLUMN_TIME_BUCKET, timestamp);

        boolQuery.must().add(matchApplicationId);
        boolQuery.must().add(matchTimeBucket);

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(100);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        List<InstPerformance> instPerformances = new LinkedList<>();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        for (SearchHit searchHit : searchHits) {
            int instanceId = (Integer)searchHit.getSource().get(InstPerformanceTable.COLUMN_INSTANCE_ID);
            int callTimes = (Integer)searchHit.getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES);
            long costTotal = ((Number)searchHit.getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL)).longValue();

            instPerformances.add(new InstPerformance(instanceId, callTimes, costTotal));
        }

        return instPerformances;
    }

    @Override public int getMetric(int instanceId, long timeBucket) {
        String id = timeBucket + Const.ID_SPLIT + instanceId;
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();

        if (getResponse.isExists()) {
            return ((Number)getResponse.getSource().get(InstPerformanceTable.COLUMN_CALL_TIMES)).intValue();
        }
        return 0;
    }

    @Override public JsonArray getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        int i = 0;
        do {
            String id = (startTimeBucket + i) + Const.ID_SPLIT + instanceId;
            prepareMultiGet.add(CpuMetricTable.TABLE, InstPerformanceTable.TABLE_TYPE, id);
            i++;
        }
        while (startTimeBucket + i <= endTimeBucket);

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
}
