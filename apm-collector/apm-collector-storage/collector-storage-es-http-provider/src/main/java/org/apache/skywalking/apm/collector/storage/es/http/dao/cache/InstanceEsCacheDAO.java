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
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
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
public class InstanceEsCacheDAO extends EsHttpDAO implements IInstanceCacheDAO {

    public InstanceEsCacheDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getApplicationId(int instanceId) {
        DocumentResult response = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId));
        if (response.isSucceeded()) {
            return response.getSourceAsObject(JsonObject.class).get(InstanceTable.COLUMN_APPLICATION_ID).getAsInt();
        } else {
            return 0;
        }
    }

    @Override public int getInstanceIdByAgentUUID(int applicationId, String agentUUID) {
        ElasticSearchHttpClient client = getClient();

        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_AGENT_UUID, agentUUID));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(builder);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(InstanceTable.TABLE).build();
        
        SearchResult result = client.execute(search);

        if (result.getTotal() > 0) {
            JsonObject searchHit =result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits").get(0).getAsJsonObject().getAsJsonObject("_source");
            return searchHit.get(InstanceTable.COLUMN_INSTANCE_ID).getAsInt();
        }
        return 0;
    }

    @Override public int getInstanceIdByAddressId(int applicationId, int addressId) {
        ElasticSearchHttpClient client = getClient();

//        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(InstanceTable.TABLE);
//        searchRequestBuilder.setTypes("type");
//        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_ADDRESS_ID, addressId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.TRUE));
//        searchRequestBuilder.setQuery(builder);
//        searchRequestBuilder.setSize(1);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(builder);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(InstanceTable.TABLE).build();
        
        SearchResult result = client.execute(search);

//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (result.getTotal() > 0) {
            JsonObject searchHit =result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits").get(0).getAsJsonObject().getAsJsonObject("_source");
            return searchHit.get(InstanceTable.COLUMN_INSTANCE_ID).getAsInt();
        }
        return 0;
    }
}
