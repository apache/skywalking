package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.skywalking.apm.collector.agentstream.worker.segment.cost.define.SegmentCostTable;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class SegmentCostEsDAO extends EsDAO implements ISegmentCostDAO {

    @Override public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, int limit, int from) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(SegmentCostTable.TABLE);
        searchRequestBuilder.setTypes(SegmentCostTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        searchRequestBuilder.setQuery(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        mustQueryList.add(QueryBuilders.rangeQuery(SegmentCostTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        if (minCost != -1 || maxCost != -1) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentCostTable.COLUMN_COST);
            if (minCost != -1) {
                rangeQueryBuilder.gte(minCost);
            }
            if (maxCost != -1) {
                rangeQueryBuilder.lte(maxCost);
            }
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (!StringUtils.isEmpty(operationName)) {
            mustQueryList.add(QueryBuilders.matchQuery(SegmentCostTable.COLUMN_OPERATION_NAME, operationName));
        }
        if (!StringUtils.isEmpty(globalTraceId)) {
            mustQueryList.add(QueryBuilders.matchQuery(SegmentCostTable.COLUMN_GLOBAL_TRACE_ID, globalTraceId));
        }

        searchRequestBuilder.addSort(SegmentCostTable.COLUMN_COST, SortOrder.DESC);
        searchRequestBuilder.setSize(limit);
        searchRequestBuilder.setFrom(from);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        JsonObject topSegPaging = new JsonObject();
        topSegPaging.addProperty("recordsTotal", searchResponse.getHits().totalHits);

        JsonArray topSegArray = new JsonArray();
        topSegPaging.add("data", topSegArray);

        int num = from;
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            JsonObject topSegmentJson = new JsonObject();
            topSegmentJson.addProperty("num", num);
            String segId = (String)searchHit.getSource().get(SegmentCostTable.COLUMN_SEGMENT_ID);
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_SEGMENT_ID, segId);
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_START_TIME, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_START_TIME));
            if (searchHit.getSource().containsKey(SegmentCostTable.COLUMN_END_TIME)) {
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_END_TIME, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_END_TIME));
            }

            topSegmentJson.addProperty(SegmentCostTable.COLUMN_OPERATION_NAME, (String)searchHit.getSource().get(SegmentCostTable.COLUMN_OPERATION_NAME));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_GLOBAL_TRACE_ID, (String)searchHit.getSource().get(SegmentCostTable.COLUMN_GLOBAL_TRACE_ID));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_COST, (Number)searchHit.getSource().get(SegmentCostTable.COLUMN_COST));
            topSegmentJson.addProperty(SegmentCostTable.COLUMN_IS_ERROR, (Boolean)searchHit.getSource().get(SegmentCostTable.COLUMN_IS_ERROR));

            num++;
            topSegArray.add(topSegmentJson);
        }

        return topSegPaging;
    }
}
