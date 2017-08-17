package org.skywalking.apm.collector.ui.dao;

import java.util.LinkedList;
import java.util.List;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.instance.InstPerformanceTable;

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
            long costTotal = (Long)searchHit.getSource().get(InstPerformanceTable.COLUMN_COST_TOTAL);

            instPerformances.add(new InstPerformance(instanceId, callTimes, costTotal));
        }

        return instPerformances;
    }
}
