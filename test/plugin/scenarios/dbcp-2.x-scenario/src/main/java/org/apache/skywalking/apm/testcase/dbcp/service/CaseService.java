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

package org.apache.skywalking.apm.testcase.dbcp.service;

import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.apache.skywalking.apm.testcase.dbcp.MysqlConfig;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

@Service
public class CaseService {

    public static DataSource DS;
    private static final String CREATE_TABLE_SQL = "CREATE TABLE test_DBCP(\n" + "id VARCHAR(1) PRIMARY KEY, \n" + "value VARCHAR(1) NOT NULL)";
    private static final String INSERT_DATA_SQL = "INSERT INTO test_DBCP(id, value) VALUES(1,1)";
    private static final String QUERY_DATA_SQL = "SELECT id, value FROM test_DBCP WHERE id=1";
    private static final String DELETE_DATA_SQL = "DELETE FROM test_DBCP WHERE id=1";
    private static final String DROP_TABLE_SQL = "DROP table test_DBCP";

    static {
        Properties properties = new Properties();
        properties.setProperty("driverClassName", "com.mysql.jdbc.Driver");
        properties.setProperty("url", MysqlConfig.getUrl());
        properties.setProperty("username", MysqlConfig.getUserName());
        properties.setProperty("password", MysqlConfig.getPassword());
        try {
            DS = BasicDataSourceFactory.createDataSource(properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testCase() {
        sqlExecutor(CREATE_TABLE_SQL);
        sqlExecutor(INSERT_DATA_SQL);
        sqlExecutor(QUERY_DATA_SQL);
        sqlExecutor(DELETE_DATA_SQL);
        sqlExecutor(DROP_TABLE_SQL);
    }

    public void sqlExecutor(String sql) {
        try (Connection conn = DS.getConnection()) {
            Statement statement = conn.createStatement();
            statement.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
