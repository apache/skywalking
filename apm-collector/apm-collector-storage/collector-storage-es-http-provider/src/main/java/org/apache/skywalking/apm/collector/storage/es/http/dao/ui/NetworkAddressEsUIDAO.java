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

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsUIDAO extends EsDAO implements INetworkAddressUIDAO {

    public NetworkAddressEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getNumOfSpanLayer(int spanLayer) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NetworkAddressTable.TABLE);
//        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_SPAN_LAYER, spanLayer));
//        searchRequestBuilder.setSize(0);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0);
        searchSourceBuilder.query(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_SPAN_LAYER, spanLayer));
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(NetworkAddressTable.TABLE)
                .addType(NetworkAddressTable.TABLE_TYPE).build();
        
        SearchResult result =  getClient().execute(search);

//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return result.getTotal().intValue();
    }
}
