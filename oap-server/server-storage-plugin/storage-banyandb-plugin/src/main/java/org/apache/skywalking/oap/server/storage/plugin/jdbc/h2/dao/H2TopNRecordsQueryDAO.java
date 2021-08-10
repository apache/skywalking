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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2TopNRecordsQueryDAO implements ITopNRecordsQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TopNRecordsQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<SelectedRecord> readSampledRecords(final TopNCondition condition,
                                                   final String valueColumnName,
                                                   final Duration duration) throws IOException {
        StringBuilder sql = new StringBuilder("select * from " + condition.getName() + " where ");
        List<Object> parameters = new ArrayList<>(10);

        if (StringUtil.isNotEmpty(condition.getParentService())) {
            sql.append(" service_id = ? and");
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            parameters.add(serviceId);
        }

        sql.append(" ").append(TopN.TIME_BUCKET).append(" >= ?");
        parameters.add(duration.getStartTimeBucketInSec());
        sql.append(" and ").append(TopN.TIME_BUCKET).append(" <= ?");
        parameters.add(duration.getEndTimeBucketInSec());

        sql.append(" order by ").append(valueColumnName);
        if (condition.getOrder().equals(Order.DES)) {
            sql.append(" desc ");
        } else {
            sql.append(" asc ");
        }
        sql.append(" limit ").append(condition.getTopN());

        List<SelectedRecord> results = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    SelectedRecord record = new SelectedRecord();
                    record.setName(resultSet.getString(TopN.STATEMENT));
                    record.setRefId(resultSet.getString(TopN.TRACE_ID));
                    record.setId(record.getRefId());
                    record.setValue(resultSet.getString(valueColumnName));
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return results;
    }

}
