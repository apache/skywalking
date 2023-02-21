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
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

/**
 * JDBC Client uses HikariCP connection management lib to execute SQL.
 */
@Slf4j
public class JDBCHikariCPClient implements Client, HealthCheckable {
    private final HikariConfig hikariConfig;
    private final DelegatedHealthChecker healthChecker;
    private HikariDataSource dataSource;

    public JDBCHikariCPClient(Properties properties) {
        hikariConfig = new HikariConfig(properties);
        this.healthChecker = new DelegatedHealthChecker();
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
    public Connection getConnection() throws JDBCClientException {
        return getConnection(true);
    }

    public Connection getConnection(boolean autoCommit) throws JDBCClientException {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(autoCommit);
            return connection;
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public void execute(String sql) throws JDBCClientException {
        if (log.isDebugEnabled()) {
            log.debug("Executing SQL: {}", sql);
        }

        try (final var connection = getConnection();
             final var statement = connection.createStatement()) {
            statement.execute(sql);
            healthChecker.health();
        } catch (SQLException e) {
            healthChecker.unHealth(e);
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public int executeUpdate(String sql, Object... params) throws JDBCClientException {
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
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public <T> T executeQuery(String sql, ResultHandler<T> resultHandler, Object... params) throws JDBCClientException {
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
        } catch (IOException | SQLException e) {
            healthChecker.unHealth(e);
            throw new JDBCClientException(sql, e);
        }
    }

    private void setStatementParam(PreparedStatement statement,
        Object[] params) throws SQLException, JDBCClientException {
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
                    throw new JDBCClientException("Unsupported data type, type=" + param.getClass().getName());
                }
            }
        }
    }

    @Override public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }

    @FunctionalInterface
    public interface ResultHandler<T> {
        T handle(ResultSet resultSet) throws SQLException, IOException;
    }
}
