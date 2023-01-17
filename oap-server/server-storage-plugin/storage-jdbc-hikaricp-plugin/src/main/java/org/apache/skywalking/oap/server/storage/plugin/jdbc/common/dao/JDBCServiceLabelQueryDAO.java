/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class JDBCServiceLabelQueryDAO implements IServiceLabelDAO {
    private JDBCHikariCPClient jdbcClient;

    @Override
    public List<String> queryAllLabels(String serviceId) throws IOException {
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select " + ServiceLabelRecord.LABEL + " from ")
                .append(ServiceLabelRecord.INDEX_NAME)
                .append(" where ").append(ServiceLabelRecord.SERVICE_ID).append(" = ?");
        condition.add(serviceId);

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]))) {
                return parseLabels(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<String> parseLabels(ResultSet resultSet) throws SQLException {
        final List<String> labels = new ArrayList<>();
        while (resultSet.next()) {
            labels.add(resultSet.getString(ServiceLabelRecord.LABEL));
        }
        return labels;
    }
}
