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
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.joda.time.DateTime;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class JDBCHistoryDeleteDAO implements IHistoryDeleteDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;
    private final JDBCTableInstaller modelInstaller;
    private final Clock clock;

    private final Map<String, Long> lastDeletedTimeBucket = new ConcurrentHashMap<>();

    @Override
    @SneakyThrows
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) {
        final var endTimeBucket = TimeBucket.getTimeBucket(clock.millis() + TimeUnit.DAYS.toMillis(1), DownSampling.Day);
        final var startTimeBucket = endTimeBucket - ttl - 1;
        log.info(
            "Deleting history data, ttl: {}, now: {}. Keep [{}, {}]",
            ttl,
            clock.millis(),
            startTimeBucket,
            endTimeBucket
        );

        final var deadline = Long.parseLong(new DateTime().minusDays(ttl).toString("yyyyMMdd"));
        final var lastSuccessDeadline = lastDeletedTimeBucket.getOrDefault(model.getName(), 0L);
        if (deadline <= lastSuccessDeadline) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "The deadline {} is less than the last success deadline {}, skip deleting history data",
                    deadline,
                    lastSuccessDeadline
                );
            }
            return;
        }

        final var ttlTables = tableHelper.getTablesInTimeBucketRange(model.getName(), startTimeBucket, endTimeBucket);
        final var tablesToDrop = new HashSet<String>();
        final var tableName = TableHelper.getTableName(model);

        try (final var conn = jdbcClient.getConnection();
             final var result = conn.getMetaData().getTables(null, null, tableName + "%", new String[]{"TABLE"})) {
            while (result.next()) {
                tablesToDrop.add(result.getString("TABLE_NAME"));
            }
        }

        ttlTables.forEach(tablesToDrop::remove);
        tablesToDrop.removeIf(it -> !it.matches(tableName + "_\\d{8}$"));
        for (final var table : tablesToDrop) {
            final var dropSql = new SQLBuilder("drop table if exists ").append(table);
            jdbcClient.executeUpdate(dropSql.toString());
        }

        // Drop additional tables
        for (final var table : tablesToDrop) {
            final var timeBucket = TableHelper.getTimeBucket(table);
            for (final var additionalTable : model.getSqlDBModelExtension().getAdditionalTables().values()) {
                final var additionalTableToDrop = TableHelper.getTable(additionalTable.getName(), timeBucket);
                final var dropSql = new SQLBuilder("drop table if exists ").append(additionalTableToDrop);
                jdbcClient.executeUpdate(dropSql.toString());
            }
        }

        // Create tables for the next day.
        final var nextTimeBucket = TimeBucket.getTimeBucket(clock.millis() + TimeUnit.DAYS.toMillis(1), DownSampling.Day);
        modelInstaller.createTable(model, nextTimeBucket);

        lastDeletedTimeBucket.put(model.getName(), deadline);
    }
}
