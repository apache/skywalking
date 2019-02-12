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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

/**
 * @author wusheng
 */
public class H2TopNRecordsQueryDAO implements ITopNRecordsQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TopNRecordsQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<TopNRecord> getTopNRecords(long startSecondTB, long endSecondTB, String metricName, int serviceId,
        int topN, Order order) throws IOException {
        StringBuilder sql = new StringBuilder("select * from " + metricName + " where ");
        List<Object> parameters = new ArrayList<>(10);

        sql.append(" service_id = ? ");
        parameters.add(serviceId);

        sql.append(" and ").append(TopN.TIME_BUCKET).append(" >= ?");
        parameters.add(startSecondTB);
        sql.append(" and ").append(TopN.TIME_BUCKET).append(" <= ?");
        parameters.add(endSecondTB);

        sql.append(" order by ").append(TopN.LATENCY);
        if (order.equals(Order.DES)) {
            sql.append(" desc ");
        } else {
            sql.append(" asc ");
        }
        sql.append(" limit ").append(topN);

        List<TopNRecord> results = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    TopNRecord record = new TopNRecord();
                    record.setStatement(resultSet.getString(TopN.STATEMENT));
                    record.setTraceId(resultSet.getString(TopN.TRACE_ID));
                    record.setLatency(resultSet.getLong(TopN.LATENCY));
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return results;
    }
}
