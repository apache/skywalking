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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Utility class to get table name for a given model.
 */
@Slf4j
@RequiredArgsConstructor
public class TableHelper {
    private final ModuleManager moduleManager;
    private final JDBCClient jdbcClient;

    public static String getTableName(Model model) {
        return model.isMetric() ? "metrics_all" :
            (model.isRecord() && !model.isSuperDataset() ? "records_all" : model.getName());
    }

    public static String getTableForWrite(Model model) {
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return tableName;
        }

        final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        return tableName + Const.UNDERSCORE + dayTimeBucket;
    }

    public static String getTable(String modelName, long timeBucket) {
        final var model = TableMetaInfo.get(modelName);
        final var tableName = getTableName(model);
        if (timeBucket == 0) {
            timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        }

        if (!model.isTimeSeries()) {
            return tableName;
        }

        return tableName + Const.UNDERSCORE + TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucket), DownSampling.Day);
    }

    public List<String> getTablesForRead(String modelName, long timeBucketStart, long timeBucketEnd) {
        final var model = TableMetaInfo.get(modelName);
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(tableName);
        }

        timeBucketStart = TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucketStart), DownSampling.Day);
        timeBucketEnd = TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucketEnd), DownSampling.Day);

        return LongStream
            .rangeClosed(timeBucketStart, timeBucketEnd)
            .distinct()
            .mapToObj(it -> tableName + "_" + it)
            .filter(table -> {
                try {
                    return jdbcClient.tableExists(table);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    public List<String> getTablesForRead(String modelName) {
        final var model = TableMetaInfo.get(modelName);

        final var ttl = model.isRecord() ?
            configs().getRecordDataTTL() :
            configs().getMetricsDataTTL();
        final var timeBucketEnd = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        final var timeBucketStart = TimeBucket.getTimeBucket(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttl), DownSampling.Day);

        return getTablesForRead(modelName, timeBucketStart, timeBucketEnd);
    }

    public static String generateId(Model model, String originalID) {
        if (model.isRecord() && !model.isSuperDataset()) {
            return generateId(model.getName(), originalID);
        }
        if (!model.isMetric()) {
            return originalID;
        }
        return generateId(model.getName(), originalID);
    }

    public static String generateId(String table, String originalID) {
        return table + Const.ID_CONNECTOR + originalID;
    }

    ConfigService configs() {
        return moduleManager
            .find(CoreModule.NAME)
            .provider()
            .getService(ConfigService.class);
    }
}
