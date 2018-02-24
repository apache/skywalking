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

package org.apache.skywalking.apm.collector.storage.es.http.dao.cache;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author peng-yongsheng
 */
public class ServiceNameEsCacheDAO extends EsHttpDAO implements IServiceNameCacheDAO {

    public ServiceNameEsCacheDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ServiceName get(int serviceId) {
        DocumentResult getResponse = getClient().prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId));


        if (getResponse.isSucceeded()) {
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            ServiceName serviceName = new ServiceName();
            serviceName.setApplicationId((source.get(ServiceNameTable.COLUMN_APPLICATION_ID)).getAsInt());
            serviceName.setServiceId(serviceId);
            serviceName.setServiceName(source.get(ServiceNameTable.COLUMN_SERVICE_NAME).getAsString());
            return serviceName;
        }
        return null;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
//        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName));
//        searchRequestBuilder.setQuery(boolQuery);
//        searchRequestBuilder.setSize(1);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();;
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(boolQuery);
        
        Search search = new Search.Builder(boolQuery.toString()).addIndex(ServiceNameTable.TABLE) .build();
        
        SearchResult result =  getClient().execute(search);

//        SearchResponse searchResponse = searchRequestBuilder.get();
        if (result.getTotal() > 0) {
            JsonObject searchHit = result.getHits(JsonObject.class).get(0).source;
            return searchHit.get(ServiceNameTable.COLUMN_SERVICE_ID).getAsInt();
        }
        return 0;
    }
}
