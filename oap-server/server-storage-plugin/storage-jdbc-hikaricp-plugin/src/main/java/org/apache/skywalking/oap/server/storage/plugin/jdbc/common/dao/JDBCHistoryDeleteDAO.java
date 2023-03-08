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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.joda.time.DateTime;

import java.util.HashSet;

@Slf4j
@RequiredArgsConstructor
public class JDBCHistoryDeleteDAO implements IHistoryDeleteDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;
    private final ModelInstaller modelInstaller;

    @Override
    @SneakyThrows
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) {
        // TODO delete old and create new tables
        final var endTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        final var startTimeBucket = endTimeBucket - ttl;

        log.info(
            "Deleting history data, ttl: {}, now: {}. Keep [{}, {}]",
            ttl,
            System.currentTimeMillis(),
            startTimeBucket,
            endTimeBucket
        );

        final var ttlTables = tableHelper.getTablesForRead(model.getName(), startTimeBucket, endTimeBucket);
        final var tablesToDrop = new HashSet<String>();

        try (final var conn = jdbcClient.getConnection();
             final var result = conn.getMetaData().getTables(null, null, TableHelper.getTableName(model) + "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tablesToDrop.add(result.getString("TABLE_NAME"));
            }
        }

        ttlTables.forEach(tablesToDrop::remove);
        tablesToDrop.removeIf(it -> !it.matches(".*_\\d{8}"));
        for (String table : tablesToDrop) {
            final var dropSql = new SQLBuilder("drop table if exists ").append(table);
            jdbcClient.executeUpdate(dropSql.toString());
        }

        long deadline;
        long minTime;
        if (model.isRecord()) {
            deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmmss"));
            minTime = 1000_00_00_00_00_00L;
        } else {
            switch (model.getDownsampling()) {
                case Minute:
                    deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmm"));
                    minTime = 1000_00_00_00_00L;
                    break;
                case Hour:
                    deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHH"));
                    minTime = 1000_00_00_00L;
                    break;
                case Day:
                    deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMdd"));
                    minTime = 1000_00_00L;
                    break;
                default:
                    return;
            }
        }
        // Delete data in additional tables
        for (final var additionalTable : model.getSqlDBModelExtension().getAdditionalTables().values()) {
            SQLBuilder additionalTableDeleteSQL = new SQLBuilder("delete from " + additionalTable.getName() + " where ")
                .append(timeBucketColumnName).append("<= ? ")
                .append(" and ")
                .append(timeBucketColumnName).append(">= ? ");
            jdbcClient.executeUpdate(additionalTableDeleteSQL.toString(), deadline, minTime);
        }
        
        modelInstaller.createTable(model);
    }
}
