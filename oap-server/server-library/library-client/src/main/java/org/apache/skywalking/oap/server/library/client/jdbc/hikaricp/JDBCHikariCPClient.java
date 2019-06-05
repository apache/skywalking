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

import com.zaxxer.hikari.*;
import java.sql.*;
import java.util.Properties;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.slf4j.*;

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

    @Override public void connect() {
        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override public void shutdown() {
    }

    /**
     * Default getConnection is not set in auto-commit.
     *
     * @return
     * @throws JDBCClientException
     */
    public Connection getConnection() throws JDBCClientException {
        return getConnection(true);
    }

    public Connection getTransactionConnection() throws JDBCClientException {
        return getConnection(false);
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

    public void execute(Connection connection, String sql) throws JDBCClientException {
        logger.debug("execute aql: {}", sql);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new JDBCClientException(e.getMessage(), e);
        }
    }

    public boolean execute(Connection connection, String sql, Object... params) throws JDBCClientException {
        logger.debug("execute query with result: {}", sql);
        boolean result;
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param instanceof String) {
                        statement.setString(i + 1, (String)param);
                    } else if (param instanceof Integer) {
                        statement.setInt(i + 1, (int)param);
                    } else if (param instanceof Double) {
                        statement.setDouble(i + 1, (double)param);
                    } else if (param instanceof Long) {
                        statement.setLong(i + 1, (long)param);
                    } else {
                        throw new JDBCClientException("Unsupported data type, type=" + param.getClass().getName());
                    }
                }
            }
            result = statement.execute();
            statement.closeOnCompletion();
        } catch (SQLException e) {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e1) {
                }
            }
            throw new JDBCClientException(e.getMessage(), e);
        }

        return result;
    }

    public ResultSet executeQuery(Connection connection, String sql, Object... params) throws JDBCClientException {
        logger.debug("execute query with result: {}", sql);
        ResultSet rs;
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param instanceof String) {
                        statement.setString(i + 1, (String)param);
                    } else if (param instanceof Integer) {
                        statement.setInt(i + 1, (int)param);
                    } else if (param instanceof Double) {
                        statement.setDouble(i + 1, (double)param);
                    } else if (param instanceof Long) {
                        statement.setLong(i + 1, (long)param);
                    } else {
                        throw new JDBCClientException("Unsupported data type, type=" + param.getClass().getName());
                    }
                }
            }
            rs = statement.executeQuery();
            statement.closeOnCompletion();
        } catch (SQLException e) {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e1) {
                }
            }
            throw new JDBCClientException(e.getMessage(), e);
        }

        return rs;
    }
}
