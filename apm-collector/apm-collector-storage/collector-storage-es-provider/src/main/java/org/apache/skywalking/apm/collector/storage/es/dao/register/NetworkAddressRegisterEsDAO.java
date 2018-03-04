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

package org.apache.skywalking.apm.collector.storage.es.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressRegisterEsDAO extends EsDAO implements INetworkAddressRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressRegisterEsDAO.class);

    public NetworkAddressRegisterEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getMaxNetworkAddressId() {
        return getMaxId(NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
    }

    @Override public int getMinNetworkAddressId() {
        return getMinId(NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
    }

    @Override public void save(NetworkAddress networkAddress) {
        logger.debug("save network address register info, address getApplicationId: {}, network address code: {}", networkAddress.getId(), networkAddress.getNetworkAddress());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, networkAddress.getNetworkAddress());
        source.put(NetworkAddressTable.COLUMN_ADDRESS_ID, networkAddress.getAddressId());
        source.put(NetworkAddressTable.COLUMN_SPAN_LAYER, networkAddress.getSpanLayer());
        source.put(NetworkAddressTable.COLUMN_SERVER_TYPE, networkAddress.getServerType());

        IndexResponse response = client.prepareIndex(NetworkAddressTable.TABLE, networkAddress.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save network address register info, address getApplicationId: {}, network address code: {}, status: {}", networkAddress.getAddressId(), networkAddress.getNetworkAddress(), response.status().name());
    }

    @Override public void update(String id, int spanLayer, int serverType) {
        ElasticSearchClient client = getClient();

        Map<String, Object> source = new HashMap<>();
        source.put(NetworkAddressTable.COLUMN_SPAN_LAYER, spanLayer);
        source.put(NetworkAddressTable.COLUMN_SERVER_TYPE, serverType);
        client.prepareUpdate(NetworkAddressTable.TABLE, id).setDoc(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
    }
}
