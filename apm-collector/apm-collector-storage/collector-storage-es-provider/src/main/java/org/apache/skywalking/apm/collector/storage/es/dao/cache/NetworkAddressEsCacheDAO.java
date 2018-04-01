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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsCacheDAO extends EsDAO implements INetworkAddressCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressEsCacheDAO.class);

    public NetworkAddressEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getAddressId(String networkAddress) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(NetworkAddressTable.TABLE);
        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, networkAddress));
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return ((Number)searchHit.getSource().get(NetworkAddressTable.COLUMN_ADDRESS_ID)).intValue();
        }
        return Const.NONE;
    }

    @Override public String getAddressById(int addressId) {
        logger.debug("get network address, address id: {}", addressId);
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(NetworkAddressTable.TABLE, String.valueOf(addressId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            return (String)getResponse.getSource().get(NetworkAddressTable.COLUMN_NETWORK_ADDRESS);
        }
        return Const.EMPTY_STRING;
    }

    @Override public NetworkAddress getAddress(int addressId) {
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(NetworkAddressTable.TABLE, String.valueOf(addressId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            NetworkAddress address = new NetworkAddress();
            address.setId((String)getResponse.getSource().get(NetworkAddressTable.COLUMN_ID));
            address.setAddressId(((Number)getResponse.getSource().get(NetworkAddressTable.COLUMN_ADDRESS_ID)).intValue());
            address.setSpanLayer(((Number)getResponse.getSource().get(NetworkAddressTable.COLUMN_SPAN_LAYER)).intValue());
            address.setServerType(((Number)getResponse.getSource().get(NetworkAddressTable.COLUMN_SERVER_TYPE)).intValue());
            address.setNetworkAddress((String)getResponse.getSource().get(NetworkAddressTable.COLUMN_NETWORK_ADDRESS));
            return address;
        }
        return null;
    }
}
