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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.TTLCalculator;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.joda.time.DateTime;

/**
 * @author wusheng
 */
public class H2HistoryDeleteDAO implements IHistoryDeleteDAO {

    private final JDBCHikariCPClient client;
    private final StorageTTL storageTTL;
    private final ModuleDefineHolder moduleDefineHolder;

    public H2HistoryDeleteDAO(ModuleDefineHolder moduleDefineHolder, JDBCHikariCPClient client, StorageTTL storageTTL) {
        this.client = client;
        this.storageTTL = storageTTL;
        this.moduleDefineHolder = moduleDefineHolder;
    }

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName) throws IOException {
        ConfigService configService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ConfigService.class);

        SQLBuilder dataDeleteSQL = new SQLBuilder("delete from " + model.getName() + " where ").append(timeBucketColumnName).append("<= ?");

        try (Connection connection = client.getConnection()) {
            TTLCalculator ttlCalculator;
            if (model.isRecord()) {
                ttlCalculator = storageTTL.recordCalculator();
            } else {
                ttlCalculator = storageTTL.metricsCalculator(model.getDownsampling());
            }
            long timeBefore = ttlCalculator.timeBefore(new DateTime(), configService.getDataTTLConfig());
            client.execute(connection, dataDeleteSQL.toString(), timeBefore);
        } catch (JDBCClientException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
