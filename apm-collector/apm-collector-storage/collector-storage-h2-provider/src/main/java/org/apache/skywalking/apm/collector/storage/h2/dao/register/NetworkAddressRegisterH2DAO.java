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

package org.apache.skywalking.apm.collector.storage.h2.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NetworkAddressRegisterH2DAO extends H2DAO implements INetworkAddressRegisterDAO {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressRegisterH2DAO.class);

    public NetworkAddressRegisterH2DAO(H2Client client) {
        super(client);
    }

    @Override
    public int getMaxNetworkAddressId() {
        return getMaxId(NetworkAddressTable.TABLE, NetworkAddressTable.ADDRESS_ID.getName());
    }

    @Override
    public int getMinNetworkAddressId() {
        return getMinId(NetworkAddressTable.TABLE, NetworkAddressTable.ADDRESS_ID.getName());
    }

    @Override
    public void save(NetworkAddress networkAddress) {
        H2Client client = getClient();

        Map<String, Object> target = new HashMap<>();
        target.put(NetworkAddressTable.ID.getName(), networkAddress.getId());
        target.put(NetworkAddressTable.NETWORK_ADDRESS.getName(), networkAddress.getNetworkAddress());
        target.put(NetworkAddressTable.ADDRESS_ID.getName(), networkAddress.getAddressId());
        target.put(NetworkAddressTable.SRC_SPAN_LAYER.getName(), networkAddress.getSrcSpanLayer());
        target.put(NetworkAddressTable.SERVER_TYPE.getName(), networkAddress.getServerType());

        String sql = SqlBuilder.buildBatchInsertSql(NetworkAddressTable.TABLE, target.keySet());
        Object[] params = target.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void update(String id, int spanLayer, int serverType) {
        H2Client client = getClient();

        Map<String, Object> source = new HashMap<>();
        source.put(NetworkAddressTable.SRC_SPAN_LAYER.getName(), spanLayer);
        source.put(NetworkAddressTable.SERVER_TYPE.getName(), serverType);

        String sql = SqlBuilder.buildBatchUpdateSql(InstanceTable.TABLE, source.keySet(), InstanceTable.INSTANCE_ID.getName());
        Object[] params = source.values().toArray(new Object[] {id});

        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
