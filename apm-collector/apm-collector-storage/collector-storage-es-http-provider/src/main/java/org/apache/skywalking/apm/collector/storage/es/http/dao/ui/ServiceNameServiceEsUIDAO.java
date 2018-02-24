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

import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author peng-yongsheng
 */
public class ServiceNameServiceEsUIDAO extends EsHttpDAO implements IServiceNameServiceUIDAO {

    public ServiceNameServiceEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getCount() {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
//        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setSize(0);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0);

        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(ServiceNameTable.TABLE).build();
        
        return getClient().execute(search).getTotal().intValue();
       
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
//        return (int)searchResponse.getHits().getTotalHits();
    }

    @Override public List<ServiceInfo> searchService(String keyword, int topN) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
//        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setSize(topN);
//
//        searchRequestBuilder.setQuery(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_SERVICE_NAME, keyword));
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_SERVICE_NAME, keyword));
        searchSourceBuilder.size(topN);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(ServiceNameTable.TABLE).build();

        JsonArray searchHits = getClient().executeForJsonArray(search);
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
//        SearchHit[] searchHits = searchResponse.getHits().getHits();

        List<ServiceInfo> serviceInfos = new LinkedList<>();
        for (JsonElement searchHit : searchHits) {
            ServiceInfo serviceInfo = new ServiceInfo();
            
            JsonObject source = searchHit.getAsJsonObject().getAsJsonObject("_source");
            
            serviceInfo.setId((source.get(ServiceNameTable.COLUMN_SERVICE_ID)).getAsInt());
            serviceInfo.setName(source.get(ServiceNameTable.COLUMN_SERVICE_NAME).getAsString());
            serviceInfos.add(serviceInfo);
        }
        return serviceInfos;
    }
}
