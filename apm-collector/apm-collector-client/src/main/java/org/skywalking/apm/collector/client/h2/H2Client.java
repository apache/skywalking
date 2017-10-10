/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.client.h2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.skywalking.apm.collector.core.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class H2Client implements Client {

    private final Logger logger = LoggerFactory.getLogger(H2Client.class);

    private Connection conn;

    @Override public void initialize() throws H2ClientException {
        try {
            Class.forName("org.h2.Driver");
            conn = DriverManager.getConnection("jdbc:h2:mem:collector");
        } catch (ClassNotFoundException | SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
    }

    @Override public void shutdown() {

    }

    public void execute(String sql) throws H2ClientException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            statement.execute(sql);
            statement.closeOnCompletion();
        } catch (SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
    }

    public void executeQuery(String sql) throws H2ClientException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                logger.debug(rs.getString("ADDRESS") + "," + rs.getString("DATA"));
            }
            statement.closeOnCompletion();
        } catch (SQLException e) {
            throw new H2ClientException(e.getMessage(), e);
        }
    }
}
