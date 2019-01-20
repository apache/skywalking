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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import java.sql.*;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.slf4j.*;

/**
 * @author wusheng, peng-yongsheng
 */
public class MySQLRegisterLockInstaller {

    private static final Logger logger = LoggerFactory.getLogger(MySQLRegisterLockInstaller.class);

    static final String LOCK_TABLE_NAME = "register_lock";

    /**
     * In MySQL lock storage, lock table created. The row lock is used in {@link MySQLRegisterTableLockDAO}
     *
     * @param client
     * @throws StorageException
     */
    public void install(Client client, MySQLRegisterTableLockDAO dao) throws StorageException {
        JDBCHikariCPClient h2Client = (JDBCHikariCPClient)client;
        SQLBuilder tableCreateSQL = new SQLBuilder("CREATE TABLE IF NOT EXISTS " + LOCK_TABLE_NAME + " (");
        tableCreateSQL.appendLine("id int PRIMARY KEY, ");
        tableCreateSQL.appendLine("sequence int, ");
        tableCreateSQL.appendLine("name VARCHAR(100)");
        tableCreateSQL.appendLine(")");

        if (logger.isDebugEnabled()) {
            logger.debug("creating table: " + tableCreateSQL.toStringInNewLine());
        }

        try (Connection connection = h2Client.getConnection()) {
            h2Client.execute(connection, tableCreateSQL.toString());

            for (Class registerSource : InventoryProcess.INSTANCE.getAllRegisterSources()) {
                Scope sourceScope = StorageEntityAnnotationUtils.getSourceScope(registerSource);
                dao.init(sourceScope);
                putIfAbsent(h2Client, connection, sourceScope.ordinal(), sourceScope.name());
            }
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void putIfAbsent(JDBCHikariCPClient h2Client, Connection connection, int scopeId,
        String scopeName) throws StorageException {
        boolean existed = false;
        try (ResultSet resultSet = h2Client.executeQuery(connection, "select 1 from " + LOCK_TABLE_NAME + " where id = " + scopeId)) {
            if (resultSet.next()) {
                existed = true;
            }
        } catch (SQLException | JDBCClientException e) {
            throw new StorageException(e.getMessage(), e);
        }
        if (!existed) {
            try (PreparedStatement statement = connection.prepareStatement("insert into " + LOCK_TABLE_NAME + "(id, sequence, name)  values (?, ?, ?)")) {
                statement.setInt(1, scopeId);
                statement.setInt(2, 1);
                statement.setString(3, scopeName);

                statement.execute();
            } catch (SQLException e) {
                throw new StorageException(e.getMessage(), e);
            }
        }
    }
}
