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
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class JDBCContinuousProfilingPolicyDAO extends JDBCSQLExecutor implements IContinuousProfilingPolicyDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    public void savePolicy(ContinuousProfilingPolicy policy) throws IOException {
        final List<ContinuousProfilingPolicy> existingPolicy = queryPolicies(Arrays.asList(policy.getServiceId()));
        SQLExecutor sqlExecutor;
        final var model = TableMetaInfo.get(ContinuousProfilingPolicy.INDEX_NAME);
        if (CollectionUtils.isNotEmpty(existingPolicy)) {
            sqlExecutor = getUpdateExecutor(model, policy, 0, new ContinuousProfilingPolicy.Builder(), null);
        } else {
            sqlExecutor = getInsertExecutor(model, policy, 0,
                new ContinuousProfilingPolicy.Builder(), new HashMapConverter.ToStorage(), null);
        }

        try (Connection connection = jdbcClient.getConnection()) {
            sqlExecutor.invoke(connection);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @SneakyThrows
    @Override
    public List<ContinuousProfilingPolicy> queryPolicies(List<String> serviceIdList) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(ContinuousProfilingPolicy.INDEX_NAME);
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>();
        condition.add(ContinuousProfilingPolicy.INDEX_NAME);
        condition.addAll(serviceIdList);

        for (String table : tables) {
            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?")
                .append(" and ").append(ContinuousProfilingPolicy.SERVICE_ID)
                .append(" in (").append(Joiner.on(",").join(serviceIdList.stream().map(s -> "?").collect(Collectors.toList())))
                .append(" )");

            return jdbcClient.executeQuery(sql.toString(), this::buildPolicies, condition.toArray(new Object[0]));
        }

        return Collections.emptyList();
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