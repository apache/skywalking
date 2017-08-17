package org.skywalking.apm.collector.ui.dao;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortMode;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.node.NodeComponentTable;
import org.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceEsDAO extends EsDAO implements IInstanceDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsDAO.class);

    @Override public Long lastHeartBeatTime() {
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gt(fiveMinuteBefore);
        return heartBeatTime(rangeQueryBuilder);
    }

    @Override public Long instanceLastHeartBeatTime(long applicationInstanceId) {
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gt(fiveMinuteBefore);
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(InstanceTable.COLUMN_INSTANCE_ID, applicationInstanceId);
        boolQueryBuilder.must(rangeQueryBuilder);
        boolQueryBuilder.must(matchQueryBuilder);
        return heartBeatTime(boolQueryBuilder);
    }

    private Long heartBeatTime(AbstractQueryBuilder queryBuilder) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(NodeComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(queryBuilder);
        searchRequestBuilder.setSize(1);
        searchRequestBuilder.setFetchSource(InstanceTable.COLUMN_HEARTBEAT_TIME, null);
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.COLUMN_HEARTBEAT_TIME).sortMode(SortMode.MAX));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        Long heartBeatTime = 0L;
        for (SearchHit searchHit : searchHits) {
            heartBeatTime = (Long)searchHit.getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME);
            logger.debug("heartBeatTime: {}", heartBeatTime);
            heartBeatTime = heartBeatTime - 5;
        }
        return heartBeatTime;
    }
}
