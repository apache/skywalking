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

package org.apache.skywalking.oap.server.library.client.jdbc.hikaricp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * JDBC Client uses HikariCP connection management lib to execute SQL.
 */
@Slf4j
public class JDBCClient implements Client, HealthCheckable {
    private final HikariConfig hikariConfig;
    private final DelegatedHealthChecker healthChecker;
    private HikariDataSource dataSource;

    public JDBCClient(Properties properties) {
        hikariConfig = new HikariConfig(properties);
        healthChecker = new DelegatedHealthChecker();
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void shutdown() {
        dataSource.close();
    }

    /**
     * Default getConnection is set in auto-commit.
     */
    public Connection getConnection() throws SQLException {
        return getConnection(true);
    }

    public Connection getConnection(boolean autoCommit) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(autoCommit);
        return connection;
    }

    public void execute(String sql) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("Executing SQL: {}", sql);
        }

        try (final var connection = getConnection();
             final var statement = connection.createStatement()) {
            statement.execute(sql);
            statement.closeOnCompletion();
            healthChecker.health();
        } catch (SQLException e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    public int executeUpdate(String sql, Object... params) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("Executing SQL: {}", sql);
            log.debug("SQL parameters: {}", params);
        }

        try (final var connection = getConnection();
             final var statement = connection.prepareStatement(sql)) {
            setStatementParam(statement, params);
            int result = statement.executeUpdate();
            statement.closeOnCompletion();
            healthChecker.health();
            return result;
        } catch (SQLException e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    public <T> T executeQuery(String sql, ResultHandler<T> resultHandler, Object... params) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("Executing SQL: {}", sql);
            log.debug("SQL parameters: {}", Arrays.toString(params));
        }
        try (final var connection = getConnection();
             final var statement = connection.prepareStatement(sql)) {
            setStatementParam(statement, params);
            try (final var rs = statement.executeQuery()) {
                healthChecker.health();
                return resultHandler.handle(rs);
            }
        } catch (SQLException e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    private void setStatementParam(PreparedStatement statement, Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof String) {
                    statement.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    statement.setInt(i + 1, (int) param);
                } else if (param instanceof Double) {
                    statement.setDouble(i + 1, (double) param);
                } else if (param instanceof Long) {
                    statement.setLong(i + 1, (long) param);
                } else {
                    throw new SQLException("Unsupported data type, type=" + param.getClass().getName());
                }
            }
        }
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }

    public boolean indexExists(final String table,
                               final String index) throws SQLException {
        try (final var connection = getConnection();
             final var resultSet = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (resultSet.next()) {
                if (resultSet.getString("INDEX_NAME").equalsIgnoreCase(index)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean tableExists(final String table) throws SQLException {
        try (final var conn = getConnection();
             final var result = conn.getMetaData().getTables(null, null, table, new String[]{})) {
            return result.next();
        }
    }

    public Set<String> getTableColumns(final String table) throws SQLException {
        try (final var conn = getConnection();
             final var result = conn.getMetaData().getColumns(null, null, table, null)) {
            final var columns = new HashSet<String>();
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
            return columns;
        }
    }

    @FunctionalInterface
    public interface ResultHandler<T> {
        T handle(ResultSet resultSet) throws SQLException;
    }
}
