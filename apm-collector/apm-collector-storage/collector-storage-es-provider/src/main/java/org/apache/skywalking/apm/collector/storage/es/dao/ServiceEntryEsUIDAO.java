/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryEsUIDAO extends EsDAO implements IServiceEntryUIDAO {

    public ServiceEntryEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

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
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, entryServiceName));
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
            String entryServiceName = (String)searchHit.getSource().get(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);

            JsonObject row = new JsonObject();
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID), entryServiceId);
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME), entryServiceName);
            row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID), applicationId);
            serviceArray.add(row);
        }

        JsonObject response = new JsonObject();
        response.addProperty("total", searchHits.totalHits);
        response.add("array", serviceArray);

        return response;
    }
}
