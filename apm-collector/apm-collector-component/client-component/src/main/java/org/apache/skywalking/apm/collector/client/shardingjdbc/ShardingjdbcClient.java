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


package org.apache.skywalking.apm.collector.client.shardingjdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.skywalking.apm.collector.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.shardingjdbc.core.api.ShardingDataSourceFactory;
import io.shardingjdbc.core.api.config.ShardingRuleConfiguration;

/**
 * @author linjiaqi
 */
public class ShardingjdbcClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ShardingjdbcClient.class);
    
    private Map<String, ShardingjdbcClientConfig> shardingjdbcClientConfig;
    
    private ShardingRuleConfiguration shardingRuleConfiguration;

    private Map<String, DataSource> shardingDataSource = new HashMap<String, DataSource>();

    private DataSource dataSource;

    public ShardingjdbcClient(Map<String, ShardingjdbcClientConfig> shardingjdbcClientConfig, ShardingRuleConfiguration shardingRuleConfiguration) {
        this.shardingjdbcClientConfig = shardingjdbcClientConfig;
        this.shardingRuleConfiguration = shardingRuleConfiguration;
    }

    @Override public void initialize() throws ShardingjdbcClientException {
        try {
            shardingjdbcClientConfig.forEach((key, value) -> {
                BasicDataSource basicDataSource = new BasicDataSource();
                basicDataSource.setDriverClassName(value.getDriverClass());
                basicDataSource.setUrl(value.getUrl());
                basicDataSource.setUsername(value.getUserName());
                basicDataSource.setPassword(value.getPassword());
                shardingDataSource.put(key, basicDataSource);
                logger.info("add sharding datasource: {}, url: {}", key, value.getUrl());
            });
            dataSource = ShardingDataSourceFactory.createDataSource(shardingDataSource, shardingRuleConfiguration,
                    new HashMap<String, Object>(), new Properties());
        } catch (Exception e) {
            logger.error("case the exception is 'Cannot load JDBC driver class', please add the driver mysql-connector-java-5.1.36.jar to collector-libs manual");
            throw new ShardingjdbcClientException(e.getMessage(), e);
        }
    }

    @Override public void shutdown() {
        
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void execute(String sql) throws ShardingjdbcClientException {
        Connection conn = null;
        Statement statement = null;
        try {
            conn = getConnection();
            statement = conn.createStatement();
            statement.execute(sql);
        } catch (SQLException e) {
            throw new ShardingjdbcClientException(e.getMessage(), e);
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                throw new ShardingjdbcClientException(e.getMessage(), e);
            }
        }
    }

    public ResultSet executeQuery(String sql, Object[] params) throws ShardingjdbcClientException {
        logger.debug("execute query with result: {}", sql);
        ResultSet rs;
        PreparedStatement statement;
        try {
            statement = getConnection().prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
            }
            rs = statement.executeQuery();
        } catch (SQLException e) {
            throw new ShardingjdbcClientException(e.getMessage(), e);
        }
        return rs;
    }

    public boolean execute(String sql, Object[] params) throws ShardingjdbcClientException {
        logger.debug("execute insert/update/delete: {}", sql);
        boolean flag;
        Connection conn = null;
        PreparedStatement statement = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(true);
            statement = conn.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
            }
            flag = statement.execute();
        } catch (SQLException e) {
            throw new ShardingjdbcClientException(e.getMessage(), e);
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                throw new ShardingjdbcClientException(e.getMessage(), e);
            }
        }
        return flag;
    }
}
