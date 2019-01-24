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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.sql.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
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
public class H2RegisterLockDAO implements IRegisterLockDAO {

    private static final Logger logger = LoggerFactory.getLogger(H2RegisterLockDAO.class);

    private JDBCHikariCPClient h2Client;

    public H2RegisterLockDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public int getId(Scope scope, RegisterSource registerSource) {
        try (Connection connection = h2Client.getTransactionConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, "select sequence from " + H2RegisterLockInstaller.LOCK_TABLE_NAME + " where id = " + scope.ordinal() + " for update");
            while (resultSet.next()) {
                int sequence = resultSet.getInt("sequence");
                sequence++;
                h2Client.execute(connection, "update " + H2RegisterLockInstaller.LOCK_TABLE_NAME + " set sequence = " + sequence + " where id = " + scope.ordinal());
                connection.commit();
                return sequence;
            }
        } catch (JDBCClientException | SQLException e) {
            logger.error("try inventory register lock for scope id={} name={} failure.", scope.ordinal(), scope.name());
            logger.error("tryLock error", e);
            return Const.NONE;
        }
        return Const.NONE;
    }
}
