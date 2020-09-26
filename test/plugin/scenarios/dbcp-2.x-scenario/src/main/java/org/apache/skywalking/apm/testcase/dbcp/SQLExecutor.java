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

package org.apache.skywalking.apm.testcase.dbcp;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class SQLExecutor implements AutoCloseable {
    public static BasicDataSource ds;
    private static Connection connection;

    public SQLExecutor() throws SQLException {
        try {
            Properties properties = new Properties();
            properties.setProperty("driverClassName", "com.mysql.jdbc.Driver");
            properties.setProperty("url", MysqlConfig.getUrl());
            properties.setProperty("username", MysqlConfig.getUserName());
            properties.setProperty("password", MysqlConfig.getPassword());
            ds = BasicDataSourceFactory.createDataSource(properties);
        } catch (Exception e) {
            //
        }
        connection = ds.getConnection();
    }

    public void createTable(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute(sql);
        statement.close();
        connection.close();
        connection = null;
    }

    public void dropTable(String sql) throws SQLException {
        connection = ds.getConnection();
        executeStatement(sql);
    }

    public void executeStatement(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute(sql);
        statement.close();
    }
    
    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public void closePool() throws SQLException {
        if (ds != null) {
            ds.close();
        }
    }

    @Override
    public void close() throws Exception {
        closeConnection();
        closePool();
    }
}
