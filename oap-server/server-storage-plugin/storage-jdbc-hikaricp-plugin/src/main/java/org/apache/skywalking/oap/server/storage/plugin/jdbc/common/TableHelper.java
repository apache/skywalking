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
import org.apache.skywalking.oap.server.core.storage.annotation.InspectQueryContext;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import java.time.Duration;
import java.util.ArrayList;
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
                    .expireAfterWrite(Duration.ofMinutes(10))
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
        final var model = TableMetaInfo.get(modelName);
        if (model == null && InspectQueryContext.get(modelName) != null) {
            // A foreign metric (admin inspect value path: InspectQueryContext active on this thread)
            // has no local model, so its physical function table is unknown. Probe every metric
            // function table; the metric-prefixed row ids (generateId) keep only this metric's rows.
            // A non-overlay miss is a genuinely unknown metric — fall through and let it surface.
            final List<String> tables = new ArrayList<>();
            for (final var rawTable : getMetricRawTables()) {
                tables.addAll(getExistingDayTables(rawTable, timeBucketStart, timeBucketEnd));
            }
            return tables;
        }
        final var rawTableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(rawTableName);
        }

        final var ttlTables = getTablesWithinTTL(modelName);
        return getTablesInTimeBucketRange(modelName, timeBucketStart, timeBucketEnd)
            .stream()
            .filter(ttlTables::contains)
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
        final var rawTableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(rawTableName);
        }

        final var timestampStart = TimeBucket.getTimestamp(timeBucketStart);
        final var timestampEnd = TimeBucket.getTimestamp(timeBucketEnd);
        final var timeBuckets = LongStream.builder();
        for (var timestamp = timestampStart; timestamp <= timestampEnd; timestamp += TimeUnit.DAYS.toMillis(1)) {
            timeBuckets.add(TimeBucket.getTimeBucket(timestamp, DownSampling.Day));
        }

        return timeBuckets
            .build()
            .distinct()
            .mapToObj(timeBucket -> getTable(rawTableName, timeBucket))
            .collect(toList());
    }

    public List<String> getTablesWithinTTL(String modelName) {
        final var model = TableMetaInfo.get(modelName);
        final var rawTableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(rawTableName);
        }

        final var ttlTimeBuckets = getTTLTimeBuckets(model);
        return ttlTimeBuckets
            .stream()
            .map(it -> getTable(rawTableName, it))
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
     * Distinct physical (raw) table names of every aggregation-FUNCTION metric model installed on
     * this node — the closed set of {@code metrics_<fn>} / {@code meter_<fn>} tables that a foreign
     * metric (defined by another OAP) must also live in. Used by the inspect probe.
     *
     * <p>Filtered to function metrics, NOT all {@code isMetric()} models: metadata "metrics" such as
     * {@code ServiceTraffic} / {@code InstanceTraffic} / {@code EndpointTraffic} are {@code Metrics}
     * subclasses (so {@code isMetric()} is true) but carry no aggregation function, no
     * {@code entity_id}, and no {@code table_name} discriminator column. Probing them with
     * {@code select entity_id ... where table_name = ?} would hit "column not found" and 500. Only
     * function metrics are merged into the shared {@code metrics_<fn>} tables and always carry both
     * columns.
     */
    public static List<String> getMetricRawTables() {
        return TableMetaInfo.getModels().stream()
            .filter(TableHelper::isFunctionMetric)
            .map(TableHelper::getTableName)
            .distinct()
            .collect(toList());
    }

    /**
     * Day-partitioned tables for a RAW physical table name (a metric function table) within a
     * time-bucket range, filtered to those that actually exist. Unlike
     * {@link #getTablesForRead(String, long, long)} this needs no local {@link Model}, so it backs
     * the foreign-metric inspect probe across the node's known function tables.
     */
    public List<String> getExistingDayTables(String rawTableName, long timeBucketStart, long timeBucketEnd) {
        final var timestampStart = TimeBucket.getTimestamp(timeBucketStart);
        final var timestampEnd = TimeBucket.getTimestamp(timeBucketEnd);
        final var timeBuckets = LongStream.builder();
        for (var timestamp = timestampStart; timestamp <= timestampEnd; timestamp += TimeUnit.DAYS.toMillis(1)) {
            timeBuckets.add(TimeBucket.getTimeBucket(timestamp, DownSampling.Day));
        }
        return timeBuckets.build()
            .distinct()
            .mapToObj(timeBucket -> getTable(rawTableName, timeBucket))
            .filter(table -> {
                try {
                    return tableExistence.get(table);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(toList());
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

    List<Long> getTTLTimeBuckets(Model model) {
        final var ttl = model.isRecord() ?
            getConfigService().getRecordDataTTL() :
            getConfigService().getMetricsDataTTL();
        return LongStream
            .range(0, ttl)
            .mapToObj(it -> TimeBucket.getTimeBucket(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it), DownSampling.Day))
            .distinct()
            .collect(toList());
    }
}
