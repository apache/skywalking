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
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsCacheDAO extends EsHttpDAO implements INetworkAddressCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressEsCacheDAO.class);

    public NetworkAddressEsCacheDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getAddressId(String networkAddress) {
        ElasticSearchHttpClient client = getClient();

//        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(NetworkAddressTable.TABLE);
//        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, networkAddress));
//        searchRequestBuilder.setSize(1);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, networkAddress));
        searchSourceBuilder.size(1);
        
        Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(NetworkAddressTable.TABLE)
                .addType(NetworkAddressTable.TABLE_TYPE)
                .build();

        SearchResult result = client.execute(search);
        result.getSourceAsString();

        //        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        //        if (searchResponse.getHits().totalHits > 0) {
        //            SearchHit searchHit = searchResponse.getHits().iterator().next();
        //            return (int)searchHit.getSource().get(NetworkAddressTable.COLUMN_ADDRESS_ID);
        //        }

        if (result.getTotal() > 0) {
            JsonArray array =  result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");
            for (Object x : array) {
                JsonObject a = (JsonObject) x; 
                return a.getAsJsonObject("_source").get("NetworkAddressTable.COLUMN_ADDRESS_ID").getAsInt();
            }
        }

        return 0;
    }

    @Override public String getAddress(int addressId) {
        logger.debug("get network address, address id: {}", addressId);
        ElasticSearchHttpClient client = getClient();
        DocumentResult getResponse = client.prepareGet(NetworkAddressTable.TABLE, String.valueOf(addressId));

        if (getResponse.isSucceeded()) {
            return getResponse.getSourceAsObject(JsonObject.class).get(NetworkAddressTable.COLUMN_NETWORK_ADDRESS).getAsString();
        }
        return Const.EMPTY_STRING;
    }
}
