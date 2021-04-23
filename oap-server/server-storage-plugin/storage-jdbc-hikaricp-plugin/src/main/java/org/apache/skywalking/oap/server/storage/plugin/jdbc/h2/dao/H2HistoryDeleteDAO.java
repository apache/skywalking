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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.joda.time.DateTime;

public class H2HistoryDeleteDAO implements IHistoryDeleteDAO {

    private final JDBCHikariCPClient client;

    public H2HistoryDeleteDAO(JDBCHikariCPClient client) {
        this.client = client;
    }

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
        SQLBuilder dataDeleteSQL = new SQLBuilder("delete from " + model.getName() + " where ")
            .append(timeBucketColumnName).append("<= ? ");

        try (Connection connection = client.getConnection()) {
            long deadline;
            if (model.isRecord()) {
                deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmmss"));
            } else {
                switch (model.getDownsampling()) {
                    case Minute:
                        deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmm"));
                        break;
                    case Hour:
                        deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHH"));
                        break;
                    case Day:
                        deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMdd"));
                        break;
                    default:
                        return;
                }
            }
            client.executeUpdate(connection, dataDeleteSQL.toString(), deadline);
        } catch (JDBCClientException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
