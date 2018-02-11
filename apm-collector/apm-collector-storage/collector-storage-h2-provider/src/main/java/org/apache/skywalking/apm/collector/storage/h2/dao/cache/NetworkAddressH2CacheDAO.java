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

package org.apache.skywalking.apm.collector.storage.h2.dao.cache;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NetworkAddressH2CacheDAO extends H2DAO implements INetworkAddressCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressH2CacheDAO.class);

    private static final String GET_ADDRESS_ID_OR_CODE_SQL = "select {0} from {1} where {2} = ?";

    public NetworkAddressH2CacheDAO(H2Client client) {
        super(client);
    }

    @Override
    public int getAddressId(String networkAddress) {
        logger.info("get the address id with network address = {}", networkAddress);
        H2Client client = getClient();

        String sql = SqlBuilder.buildSql(GET_ADDRESS_ID_OR_CODE_SQL, NetworkAddressTable.COLUMN_ADDRESS_ID, NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_NETWORK_ADDRESS);

        Object[] params = new Object[] {networkAddress};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.NONE;
    }

    @Override public String getAddressById(int addressId) {
        logger.debug("get network address, address id: {}", addressId);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_ADDRESS_ID_OR_CODE_SQL, NetworkAddressTable.COLUMN_NETWORK_ADDRESS, NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
        Object[] params = new Object[] {addressId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.EMPTY_STRING;
    }

    @Override public NetworkAddress getAddress(int addressId) {
        logger.debug("get network address, address id: {}", addressId);
        H2Client client = getClient();

        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, NetworkAddressTable.TABLE, NetworkAddressTable.COLUMN_ADDRESS_ID);
        Object[] params = new Object[] {addressId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                NetworkAddress networkAddress = new NetworkAddress();
                networkAddress.setId(rs.getString(NetworkAddressTable.COLUMN_ID));
                networkAddress.setAddressId(rs.getInt(NetworkAddressTable.COLUMN_ADDRESS_ID));
                networkAddress.setNetworkAddress(rs.getString(NetworkAddressTable.COLUMN_NETWORK_ADDRESS));
                networkAddress.setSpanLayer(rs.getInt(NetworkAddressTable.COLUMN_SPAN_LAYER));
                networkAddress.setServerType(rs.getInt(NetworkAddressTable.COLUMN_SERVER_TYPE));
                return networkAddress;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
