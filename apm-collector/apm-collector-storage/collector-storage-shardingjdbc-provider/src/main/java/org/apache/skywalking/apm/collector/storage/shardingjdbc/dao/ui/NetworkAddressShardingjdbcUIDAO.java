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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalApp;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class NetworkAddressShardingjdbcUIDAO extends ShardingjdbcDAO implements INetworkAddressUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressShardingjdbcUIDAO.class);

    public NetworkAddressShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public int getNumOfSpanLayer(int srcSpanLayer) {
        String dynamicSql = "select count({0}) as cnt from {1} where {2} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, NetworkAddressTable.NETWORK_ADDRESS.getName(), NetworkAddressTable.TABLE, NetworkAddressTable.SRC_SPAN_LAYER.getName());
        Object[] params = new Object[] {srcSpanLayer};

        try (
                ResultSet rs = getClient().executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public List<ConjecturalApp> getConjecturalApps() {
        String dynamicSql = "select {0}, count({0}) as cnt from {1} where {2} in (?, ?, ?) group by {0} limit 100";
        String sql = SqlBuilder.buildSql(dynamicSql, NetworkAddressTable.SERVER_TYPE.getName(), NetworkAddressTable.TABLE, NetworkAddressTable.SRC_SPAN_LAYER.getName());
        Object[] params = new Object[] {SpanLayer.Database_VALUE, SpanLayer.Cache_VALUE, SpanLayer.MQ_VALUE};

        List<ConjecturalApp> conjecturalApps = new LinkedList<>();
        try (
                ResultSet rs = getClient().executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                ConjecturalApp conjecturalApp = new ConjecturalApp();
                conjecturalApp.setId(rs.getInt(NetworkAddressTable.SERVER_TYPE.getName()));
                conjecturalApp.setNum(rs.getInt("cnt"));
                conjecturalApps.add(conjecturalApp);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return conjecturalApps;
    }
}
