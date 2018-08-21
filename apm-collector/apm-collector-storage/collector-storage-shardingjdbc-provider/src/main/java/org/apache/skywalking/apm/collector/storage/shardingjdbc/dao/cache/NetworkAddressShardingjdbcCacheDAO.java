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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class NetworkAddressShardingjdbcCacheDAO extends ShardingjdbcDAO implements INetworkAddressCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressShardingjdbcCacheDAO.class);

    private static final String GET_ADDRESS_ID_OR_CODE_SQL = "select {0} from {1} where {2} = ?";

    public NetworkAddressShardingjdbcCacheDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override
    public int getAddressId(String networkAddress) {
        logger.info("get the address id with network address = {}", networkAddress);
        ShardingjdbcClient client = getClient();

        String sql = SqlBuilder.buildSql(GET_ADDRESS_ID_OR_CODE_SQL, NetworkAddressTable.ADDRESS_ID.getName(), NetworkAddressTable.TABLE, NetworkAddressTable.NETWORK_ADDRESS.getName());

        Object[] params = new Object[] {networkAddress};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.NONE;
    }

    @Override public NetworkAddress getAddressById(int addressId) {
        logger.debug("get network address, address id: {}", addressId);
        ShardingjdbcClient client = getClient();

        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, NetworkAddressTable.TABLE, NetworkAddressTable.ADDRESS_ID.getName());
        Object[] params = new Object[] {addressId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                NetworkAddress networkAddress = new NetworkAddress();
                networkAddress.setId(rs.getString(NetworkAddressTable.ID.getName()));
                networkAddress.setAddressId(rs.getInt(NetworkAddressTable.ADDRESS_ID.getName()));
                networkAddress.setNetworkAddress(rs.getString(NetworkAddressTable.NETWORK_ADDRESS.getName()));
                networkAddress.setSrcSpanLayer(rs.getInt(NetworkAddressTable.SRC_SPAN_LAYER.getName()));
                networkAddress.setServerType(rs.getInt(NetworkAddressTable.SERVER_TYPE.getName()));
                return networkAddress;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
