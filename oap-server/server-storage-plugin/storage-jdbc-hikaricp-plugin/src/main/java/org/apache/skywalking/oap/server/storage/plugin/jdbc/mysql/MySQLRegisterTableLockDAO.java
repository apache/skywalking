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
import java.util.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.*;

/**
 * In MySQL, use a row lock of LOCK table.
 *
 * @author wusheng, peng-yongsheng
 */
public class MySQLRegisterTableLockDAO implements IRegisterLockDAO {

    private static final Logger logger = LoggerFactory.getLogger(MySQLRegisterTableLockDAO.class);

    private JDBCHikariCPClient h2Client;
    private Map<Scope, Connection> onLockingConnection;

    public MySQLRegisterTableLockDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
        onLockingConnection = new HashMap<>();
    }

    void init(Scope scope) {
        if (!onLockingConnection.containsKey(scope)) {
            onLockingConnection.put(scope, null);
        }
    }

    @Override public int tryLockAndIncrement(Scope scope) {
        if (onLockingConnection.containsKey(scope)) {
            try {
                Connection connection = h2Client.getTransactionConnection();
                onLockingConnection.put(scope, connection);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                ResultSet resultSet = h2Client.executeQuery(connection, "select sequence from " + MySQLRegisterLockInstaller.LOCK_TABLE_NAME + " where id = " + scope.ordinal() + " for update");
                while (resultSet.next()) {
                    int sequence = resultSet.getInt("sequence");
                    sequence++;
                    h2Client.execute(connection, "update " + MySQLRegisterLockInstaller.LOCK_TABLE_NAME + " set sequence = " + sequence + " where id = " + scope.ordinal());
                    return sequence;
                }
            } catch (JDBCClientException | SQLException e) {
                logger.error("try inventory register lock for scope id={} name={} failure.", scope.ordinal(), scope.name());
                logger.error("tryLock error", e);
                return Const.NONE;
            }
        }
        return Const.NONE;
    }

    @Override public void releaseLock(Scope scope) {
        Connection connection = onLockingConnection.get(scope);
        if (connection != null) {
            try {
                connection.commit();
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.close();
            } catch (SQLException e) {
                logger.error("release lock failure.", e);
            } finally {
                onLockingConnection.put(scope, null);
            }
        }
    }
}
