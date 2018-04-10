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

package org.apache.skywalking.apm.collector.client.h2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.skywalking.apm.collector.client.Client;
import org.h2.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class H2Client implements Client {

    private final Logger logger = LoggerFactory.getLogger(H2Client.class);

    private Connection conn;
    private String url;
    private String userName;
    private String password;

    public H2Client() {
        this.url = "jdbc:h2:mem:collector";
        this.userName = "";
        this.password = "";
    }

    public H2Client(String url, String userName, String password) {
        this.url = url;
        this.userName = userName;
        this.password = password;
    }

    @Override public void initialize() throws H2ClientException {
        try {
            Class.forName("org.h2.Driver");
            conn = DriverManager.
                getConnection(this.url, this.userName, this.password);
        } catch (Exception e) {
            throw new H2ClientException(e.getMessage(), e);
        }
    }

    @Override public void shutdown() {
        IOUtils.closeSilently(conn);
    }

    public Connection getConnection() {
        return conn;
    }

    public void execute(String sql) throws H2ClientException {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute(sql);
            statement.closeOnCompletion();
        } catch (SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
    }

    public ResultSet executeQuery(String sql, Object[] params) throws H2ClientException {
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
            statement.closeOnCompletion();
        } catch (SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
        return rs;
    }

    public boolean execute(String sql, Object[] params) throws H2ClientException {
        logger.debug("execute insert/update/delete: {}", sql);
        boolean flag;
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            conn.setAutoCommit(true);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
            }
            flag = statement.execute();
        } catch (SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
        return flag;
    }
}
