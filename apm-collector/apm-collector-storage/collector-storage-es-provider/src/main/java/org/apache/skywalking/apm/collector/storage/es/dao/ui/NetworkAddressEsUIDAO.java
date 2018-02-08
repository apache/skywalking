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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsUIDAO extends EsDAO implements INetworkAddressUIDAO {

    public NetworkAddressEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getNumOfSpanLayer(int spanLayer) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NetworkAddressTable.TABLE);
        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_SPAN_LAYER, spanLayer));
        searchRequestBuilder.setSize(0);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return (int)searchResponse.getHits().getTotalHits();
    }
}
