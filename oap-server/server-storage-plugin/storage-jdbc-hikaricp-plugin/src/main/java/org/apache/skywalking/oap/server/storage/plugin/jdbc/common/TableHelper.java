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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Range;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static java.util.stream.Collectors.toList;

/**
 * Utility class to get table name for a given model.
 */
@Slf4j
@RequiredArgsConstructor
public class TableHelper {
    private final ModuleManager moduleManager;
    private final JDBCClient jdbcClient;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final ConfigService configService = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);

    private final LoadingCache<String, Boolean> tableExistence =
        CacheBuilder.newBuilder()
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .build(new CacheLoader<>() {
                        @Override
                        public @NonNull Boolean load(@NonNull String tableName) throws Exception {
                            return jdbcClient.tableExists(tableName);
                        }
                    });

    public static String getTableName(Model model) {
        final var aggFuncName = FunctionCategory.uniqueFunctionName(model.getStreamClass()).replaceAll("-", "_");
        return StringUtil.isNotBlank(aggFuncName) ? aggFuncName : model.getName();
    }

    public static String getLatestTableForWrite(Model model) {
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return tableName;
        }

        final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        return tableName + Const.UNDERSCORE + dayTimeBucket;
    }

    public static String getTable(Model model, long timeBucket) {
        final var tableName = getTableName(model);
        if (timeBucket == 0) {
            timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        }

        if (!model.isTimeSeries()) {
            return tableName;
        }

        return tableName + Const.UNDERSCORE + TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucket), DownSampling.Day);
    }

    public static String getTable(String rawTableName, long timeBucket) {
        if (timeBucket == 0) {
            timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        }

        return rawTableName + Const.UNDERSCORE + TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucket), DownSampling.Day);
    }

    public List<String> getTablesForRead(String modelName, long timeBucketStart, long timeBucketEnd) {
        return getTablesInTimeBucketRange(modelName, timeBucketStart, timeBucketEnd)
            .stream()
            .filter(table -> {
                try {
                    return tableExistence.get(table);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(toList());
    }

    /**
     * Similar to {@link #getTablesForRead(String, long, long)}, but don't check the table existence.
     */
    public List<String> getTablesInTimeBucketRange(String modelName, long timeBucketStart, long timeBucketEnd) {
        final var model = TableMetaInfo.get(modelName);
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(tableName);
        }

        timeBucketStart = TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucketStart), DownSampling.Day);
        timeBucketEnd = TimeBucket.getTimeBucket(TimeBucket.getTimestamp(timeBucketEnd), DownSampling.Day);

        final var ttlTimeBucketRange = getTTLTimeBucketRange(model);

        return LongStream
            .rangeClosed(timeBucketStart, timeBucketEnd)
            .distinct()
            .filter(ttlTimeBucketRange::contains)
            .mapToObj(it -> tableName + "_" + it)
            .collect(toList());
    }

    public List<String> getTablesWithinTTL(String modelName) {
        final var model = TableMetaInfo.get(modelName);
        final var range = getTTLTimeBucketRange(model);
        return getTablesForRead(modelName, range.lowerEndpoint(), range.upperEndpoint());
    }

    public static String generateId(Model model, String originalID) {
        if (model.isRecord() && !model.isSuperDataset()) {
            return generateId(model.getName(), originalID);
        }
        if (!model.isMetric() || !isFunctionMetric(model)) {
            return originalID;
        }
        return generateId(model.getName(), originalID);
    }

    public static String generateId(String modelName, String originalID) {
        return modelName + Const.ID_CONNECTOR + originalID;
    }

    public static boolean isFunctionMetric(Model model) {
        return StringUtil.isNotBlank(FunctionCategory.uniqueFunctionName(model.getStreamClass()));
    }

    public static long getTimeBucket(String table) {
        final var split = table.split("_");
        return Long.parseLong(split[split.length - 1]);
    }

    Range<Long> getTTLTimeBucketRange(Model model) {
        final var ttl = model.isRecord() ?
            getConfigService().getRecordDataTTL() :
            getConfigService().getMetricsDataTTL();
        final var timeBucketEnd = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        final var timeBucketStart = TimeBucket.getTimeBucket(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttl), DownSampling.Day);
        return Range.closed(timeBucketStart, timeBucketEnd);
    }
}
