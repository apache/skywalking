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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.common.base.Joiner;
import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class JDBCContinuousProfilingPolicyDAO extends JDBCSQLExecutor implements IContinuousProfilingPolicyDAO {
    private JDBCHikariCPClient jdbcClient;

    @Override
    public void savePolicy(ContinuousProfilingPolicy policy) throws IOException {
        final List<ContinuousProfilingPolicy> existingPolicy = queryPolicies(Arrays.asList(policy.getServiceId()));
        SQLExecutor sqlExecutor;
        if (CollectionUtils.isNotEmpty(existingPolicy)) {
            sqlExecutor = getUpdateExecutor(ContinuousProfilingPolicy.INDEX_NAME, policy, new ContinuousProfilingPolicy.Builder(), null);
        } else {
            sqlExecutor = getInsertExecutor(ContinuousProfilingPolicy.INDEX_NAME, policy,
                new ContinuousProfilingPolicy.Builder(), new HashMapConverter.ToStorage(), null);
        }

        try (Connection connection = jdbcClient.getConnection()) {
            sqlExecutor.invoke(connection);
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<ContinuousProfilingPolicy> queryPolicies(List<String> serviceIdList) throws IOException {
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(serviceIdList);
        sql.append("select * from ").append(ContinuousProfilingPolicy.INDEX_NAME).append(" where ")
            .append(ContinuousProfilingPolicy.SERVICE_ID)
            .append(" in (").append(Joiner.on(",").join(serviceIdList.stream().map(s -> "?").collect(Collectors.toList())))
            .append(" )");

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildPolicies(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<ContinuousProfilingPolicy> buildPolicies(ResultSet resultSet) throws SQLException {
        List<ContinuousProfilingPolicy> policies = new ArrayList<>();
        while (resultSet.next()) {
            final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
            policy.setServiceId(resultSet.getString(ContinuousProfilingPolicy.SERVICE_ID));
            policy.setUuid(resultSet.getString(ContinuousProfilingPolicy.UUID));
            policy.setConfigurationJson(resultSet.getString(ContinuousProfilingPolicy.CONFIGURATION_JSON));

            policies.add(policy);
        }
        return policies;
    }
}