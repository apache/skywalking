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

    public String getTable(String modelName, long timeBucket) {
        final var model = TableMetaInfo.get(modelName);
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return tableName;
        }

        return tableName + Const.UNDERSCORE + TimeBucket.getTimeBucket(timeBucket, DownSampling.Day);
    }

    public List<String> getTablesForRead(String modelName, long timeBucketStart, long timeBucketEnd) {
        final var model = TableMetaInfo.get(modelName);
        final var tableName = getTableName(model);

        if (!model.isTimeSeries()) {
            return Collections.singletonList(tableName);
        }

        return LongStream
            .rangeClosed(timeBucketStart, timeBucketEnd)
            .distinct()
            .mapToObj(it -> tableName + "_" + it)
            .filter(table -> {
                try {
                    return jdbcClient.isTableExisted(table);
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
        final var timeBucketStart = timeBucketEnd - ttl;

        return getTablesForRead(modelName, timeBucketStart, timeBucketEnd);
    }

    ConfigService configs() {
        return moduleManager
            .find(CoreModule.NAME)
            .provider()
            .getService(ConfigService.class);
    }
}
