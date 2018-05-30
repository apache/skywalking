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

package org.apache.skywalking.apm.collector.storage.es.dao.cache;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsCacheDAO extends EsDAO implements INetworkAddressCacheDAO {

    public NetworkAddressEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getAddressId(String networkAddress) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(NetworkAddressTable.TABLE);
        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.NETWORK_ADDRESS.getName(), networkAddress));
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return ((Number)searchHit.getSource().get(NetworkAddressTable.ADDRESS_ID.getName())).intValue();
        }
        return Const.NONE;
    }

    @Override public NetworkAddress getAddressById(int addressId) {
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(NetworkAddressTable.TABLE, String.valueOf(addressId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            NetworkAddress address = new NetworkAddress();
            address.setId((String)getResponse.getSource().get(NetworkAddressTable.ID.getName()));
            address.setAddressId(((Number)getResponse.getSource().get(NetworkAddressTable.ADDRESS_ID.getName())).intValue());
            address.setSrcSpanLayer(((Number)getResponse.getSource().get(NetworkAddressTable.SRC_SPAN_LAYER.getName())).intValue());
            address.setServerType(((Number)getResponse.getSource().get(NetworkAddressTable.SERVER_TYPE.getName())).intValue());
            address.setNetworkAddress((String)getResponse.getSource().get(NetworkAddressTable.NETWORK_ADDRESS.getName()));
            return address;
        }
        return null;
    }
}
