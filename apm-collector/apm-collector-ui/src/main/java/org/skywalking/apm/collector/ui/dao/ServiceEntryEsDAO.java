package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;

/**
 * @author pengys5
 */
public class ServiceEntryEsDAO extends EsDAO implements IServiceEntryDAO {

    @Override
    public JsonObject load(int applicationId, String entryServiceName, long startTime, long endTime, int from,
        int size) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceEntryTable.TABLE);
        searchRequestBuilder.setTypes(ServiceEntryTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ServiceEntryTable.COLUMN_REGISTER_TIME).lte(endTime));
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ServiceEntryTable.COLUMN_NEWEST_TIME).gte(startTime));

        if (applicationId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(ServiceEntryTable.COLUMN_APPLICATION_ID, applicationId));
        }
        if (StringUtils.isNotEmpty(entryServiceName)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, entryServiceName));
        }

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(size);
        searchRequestBuilder.setFrom(from);
        searchRequestBuilder.addSort(SortBuilders.fieldSort(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME).order(SortOrder.ASC));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        return parseResponse(searchResponse);
    }

    private JsonObject parseResponse(SearchResponse searchResponse) {
        SearchHits searchHits = searchResponse.getHits();

        JsonArray serviceArray = new JsonArray();
        for (SearchHit searchHit : searchHits.getHits()) {
            int applicationId = ((Number)searchHit.getSource().get(ServiceEntryTable.COLUMN_APPLICATION_ID)).intValue();
            int entryServiceId = ((Number)searchHit.getSource().get(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID)).intValue();
            String applicationCode = ApplicationCache.getForUI(applicationId);
            String entryServiceName = (String)searchHit.getSource().get(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);

            JsonObject row = new JsonObject();
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID), entryServiceId);
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME), entryServiceName);
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID), applicationId);
            row.addProperty("applicationCode", applicationCode);
            serviceArray.add(row);
        }

        JsonObject response = new JsonObject();
        response.addProperty("total", searchHits.totalHits);
        response.add("array", serviceArray);

        return response;
    }
}
