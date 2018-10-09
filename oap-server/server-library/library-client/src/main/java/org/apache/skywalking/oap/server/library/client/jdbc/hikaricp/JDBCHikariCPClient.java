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
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.ClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC Client uses HikariCP connection management lib to execute SQL.
 *
 * @author wusheng
 */
public class JDBCHikariCPClient implements Client {
    private final Logger logger = LoggerFactory.getLogger(JDBCHikariCPClient.class);

    private HikariDataSource dataSource;
    private HikariConfig hikariConfig;

    public JDBCHikariCPClient(Properties properties) {
        hikariConfig = new HikariConfig(properties);
    }

    @Override public void initialize() throws ClientException {
        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override public void shutdown() {
    }

    public Connection getConnection() throws JDBCClientException {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public void execute(String sql) throws JDBCClientException {
        try (Connection conn = getConnection()) {
            try (Statement statement = conn.createStatement()) {
                statement.execute(sql);
                statement.closeOnCompletion();
            } catch (SQLException e) {
                throw new JDBCClientException(e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public ResultSet executeQuery(String sql, List<Object> params) throws JDBCClientException {
        return executeQuery(sql, params.toArray(new Object[0]));
    }

    public ResultSet executeQuery(String sql, Object... params) throws JDBCClientException {
        logger.debug("execute query with result: {}", sql);
        ResultSet rs;
        try (Connection conn = getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        statement.setObject(i + 1, params[i]);
                    }
                }
                rs = statement.executeQuery();
                statement.closeOnCompletion();
            } catch (SQLException e) {
                throw new JDBCClientException(e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }

        return rs;
    }

    public boolean execute(String sql, Object[] params) throws JDBCClientException {
        logger.debug("execute insert/update/delete: {}", sql);
        boolean flag;
        try (Connection conn = getConnection()) {
            /**
             * Notice, SkyWalking is an observability system,
             * no transaction required.
             */
            conn.setAutoCommit(true);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                conn.setAutoCommit(true);
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        statement.setObject(i + 1, params[i]);
                    }
                }
                flag = statement.execute();
                statement.closeOnCompletion();
            } catch (SQLException e) {
                throw new JDBCClientException(e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }
        return flag;
    }
}
