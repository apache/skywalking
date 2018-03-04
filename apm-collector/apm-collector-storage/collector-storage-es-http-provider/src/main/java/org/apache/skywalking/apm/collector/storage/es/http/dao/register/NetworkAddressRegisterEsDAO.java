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

package org.apache.skywalking.apm.collector.storage.es.http.dao.register;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cyberdak
 */
public class NetworkAddressRegisterEsDAO extends EsHttpDAO implements INetworkAddressRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressRegisterEsDAO.class);

    public NetworkAddressRegisterEsDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getMaxNetworkAddressId() {
        return getMaxId(NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
    }

    @Override public int getMinNetworkAddressId() {
        return getMinId(NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
    }

    @Override public void save(NetworkAddress networkAddress) {
        logger.debug("save network address register info, address getId: {}, network address code: {}", networkAddress.getId(), networkAddress.getNetworkAddress());
        ElasticSearchHttpClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, networkAddress.getNetworkAddress());
        source.put(NetworkAddressTable.COLUMN_ADDRESS_ID, networkAddress.getAddressId());
        source.put(NetworkAddressTable.COLUMN_SPAN_LAYER, networkAddress.getSpanLayer());

        boolean response = client.prepareIndex(NetworkAddressTable.TABLE, networkAddress.getId(),source,true);
        logger.debug("save network address register info, address getId: {}, network address code: {}, status: {}", networkAddress.getAddressId(), networkAddress.getNetworkAddress(), response);
    }
}
