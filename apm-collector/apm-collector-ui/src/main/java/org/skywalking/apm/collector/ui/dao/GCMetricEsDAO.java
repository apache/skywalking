package org.skywalking.apm.collector.ui.dao;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.define.jvm.GCMetricTable;
import org.skywalking.apm.network.proto.GCPhrase;

/**
 * @author pengys5
 */
public class GCMetricEsDAO extends EsDAO implements IGCMetricDAO {

    @Override public GCCount getGCCount(long timestamp, int instanceId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GCMetricTable.TABLE);
        searchRequestBuilder.setTypes(GCMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        MatchQueryBuilder matchApplicationId = QueryBuilders.matchQuery(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, instanceId);
        MatchQueryBuilder matchTimeBucket = QueryBuilders.matchQuery(GCMetricTable.COLUMN_TIME_BUCKET, timestamp);

        boolQuery.must().add(matchApplicationId);
        boolQuery.must().add(matchTimeBucket);

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(100);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        GCCount gcCount = new GCCount();
        for (SearchHit searchHit : searchHits) {
            int phrase = (Integer)searchHit.getSource().get(GCMetricTable.COLUMN_PHRASE);
            int count = (Integer)searchHit.getSource().get(GCMetricTable.COLUMN_COUNT);

            if (phrase == GCPhrase.NEW_VALUE) {
                gcCount.setYoung(count);
            } else if (phrase == GCPhrase.OLD_VALUE) {
                gcCount.setOld(count);
            }
        }

        return gcCount;
    }
}
