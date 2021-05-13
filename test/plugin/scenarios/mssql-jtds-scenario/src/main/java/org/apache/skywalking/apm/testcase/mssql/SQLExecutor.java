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

package org.apache.skywalking.apm.testcase.mssql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLExecutor implements AutoCloseable {
    private Connection connection;

    public SQLExecutor() throws SQLException {
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        connection = DriverManager.getConnection(MssqlConfig.getUrl(), MssqlConfig.getUserName(), MssqlConfig.getPassword());
    }

    public void createTable(String sql) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.execute();
        preparedStatement.close();
    }

    public void insertData(String sql, String id, String value) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, id);
        preparedStatement.setString(2, value);
        preparedStatement.execute();
        preparedStatement.close();
    }

    public void dropTable(String sql) throws SQLException {
        executeStatement(sql);
    }
    
    public void createProcedure(String sql) throws SQLException {
        executeStatement(sql);
    }
    
    public void dropProcedure(String sql) throws SQLException {
        executeStatement(sql);
    }
    
    public void callProcedure(String sql, String id) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareCall(sql);
        preparedStatement.setString(1, id);
        preparedStatement.execute();
        preparedStatement.close();
    }
    
    public void executeStatement(String sql) throws SQLException {
        Statement preparedStatement = connection.createStatement();
        preparedStatement.execute(sql);
        preparedStatement.close();
    }
    
    public void closeConnection() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
        }
    }

    @Override
    public void close() throws Exception {
        closeConnection();
    }
}
