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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.searchbox.core.Search;

/**
 * @author cyberdak
 */
public class GlobalTraceEsUIDAO extends EsHttpDAO implements IGlobalTraceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsUIDAO.class);

    public GlobalTraceEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<String> getGlobalTraceId(String segmentId) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GlobalTraceTable.TABLE);
//        searchRequestBuilder.setTypes(GlobalTraceTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.termQuery(GlobalTraceTable.COLUMN_SEGMENT_ID, segmentId));
//        searchRequestBuilder.setSize(10);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.termQuery(GlobalTraceTable.COLUMN_SEGMENT_ID, segmentId));
        searchSourceBuilder.size(10);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(GlobalTraceTable.TABLE)
                .addType(GlobalTraceTable.TABLE_TYPE).build();

//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        
        JsonArray array =  getClient().executeForJsonArray(search);

        List<String> globalTraceIds = new ArrayList<>();
//        SearchHit[] searchHits = searchResponse.getHits().getHits();
        for (JsonElement searchHit : array) {
            String globalTraceId = searchHit.getAsJsonObject().getAsJsonObject("_source").get(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID).getAsString();
            logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
            globalTraceIds.add(globalTraceId);
        }
        return globalTraceIds;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GlobalTraceTable.TABLE);
//        searchRequestBuilder.setTypes(GlobalTraceTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.termQuery(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, globalTraceId));
//        searchRequestBuilder.setSize(10);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        
        searchSourceBuilder.size(10);
        searchSourceBuilder.query(QueryBuilders.termQuery(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, globalTraceId));
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(GlobalTraceTable.TABLE).addType(GlobalTraceTable.TABLE_TYPE).build();
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        
        JsonArray result =  getClient().executeForJsonArray(search);

        List<String> segmentIds = new ArrayList<>();
//        SearchHit[] searchHits = searchResponse.getHits().getHits();
        for (JsonElement searchHit : result) {
            String segmentId = searchHit.getAsJsonObject().getAsJsonObject("_source").get(GlobalTraceTable.COLUMN_SEGMENT_ID).getAsString();
            logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
            segmentIds.add(segmentId);
        }
        return segmentIds;
    }
}
