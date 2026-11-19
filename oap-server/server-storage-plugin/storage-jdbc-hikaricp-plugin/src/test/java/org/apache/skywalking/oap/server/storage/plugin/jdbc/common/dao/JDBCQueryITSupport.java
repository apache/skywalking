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

import java.io.Closeable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.BatchSQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.PostgreSQLTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLMetricsQueryDAO;
import org.joda.time.DateTime;
import org.mockito.MockedStatic;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A JDBC storage on a PostgreSQL or MariaDB test container, with OAP's own table install and write paths, for the
 * ITs that read rows back through OAP's JDBC query DAOs. Each target uses the installer and the DAOs its storage
 * provider registers: {@code postgresql} its own installer and metrics/aggregation DAOs, {@code mysql} (MariaDB is
 * served by the {@code mysql} storage) the MySQL installer and the common DAOs.
 *
 * <p>The fixtures sit on both sides of today's local midnight, so a read crosses two day tables, as JDBC splits
 * records and metrics per day, and both tables are inside the TTL the table lookups apply.
 */
final class JDBCQueryITSupport implements Closeable {
    /** Today's local midnight: the day tables the ITs write are yesterday's and today's. */
    static final long MIDNIGHT = new DateTime().withTimeAtStartOfDay().getMillis();
    static final int RECORD_TTL_DAYS = 3;
    static final int METRICS_TTL_DAYS = 7;

    enum Target {
        POSTGRESQL,
        MARIADB
    }

    private final Target target;
    @Getter
    private final JDBCClient client;
    @Getter
    private final ModuleManager moduleManager;
    @Getter
    private final TableHelper tableHelper;
    private final JDBCTableInstaller installer;
    private final StorageModels models;

    private JDBCQueryITSupport(final Target target, final JDBCClient client, final ModuleManager moduleManager,
                               final StorageModels models) {
        this.target = target;
        this.client = client;
        this.moduleManager = moduleManager;
        this.models = models;
        this.tableHelper = new TableHelper(moduleManager, client);
        this.installer = target == Target.POSTGRESQL
            ? new PostgreSQLTableInstaller(client, moduleManager)
            : new MySQLTableInstaller(client, moduleManager);
    }

    static PostgreSQLContainer<?> postgreSQL() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:13"));
    }

    static MariaDBContainer<?> mariaDB() {
        return new MariaDBContainer<>(DockerImageName.parse("mariadb:11.4"));
    }

    static JDBCQueryITSupport connect(final JdbcDatabaseContainer<?> server, final Target target) {
        final Properties properties = new Properties();
        properties.setProperty("jdbcUrl", server.getJdbcUrl());
        properties.setProperty("dataSource.user", server.getUsername());
        properties.setProperty("dataSource.password", server.getPassword());
        final JDBCClient client = new JDBCClient(properties);
        client.connect();

        final ConfigService configService = mock(ConfigService.class);
        when(configService.getRecordDataTTL()).thenReturn(RECORD_TTL_DAYS);
        when(configService.getMetricsDataTTL()).thenReturn(METRICS_TTL_DAYS);
        final StorageModels models = new StorageModels();
        final Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(ConfigService.class, configService);
        services.put(ModelManipulator.class, models);
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(anyString())).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(any())).thenAnswer(invocation -> services.get(invocation.<Class<?>>getArgument(0)));
        final JDBCQueryITSupport support = new JDBCQueryITSupport(target, client, moduleManager, models);
        // As the storage provider starts it: the installer renames the columns whose core names the database
        // reserves (value, precision, match) before any model is added.
        support.installer.start();
        return support;
    }

    /**
     * Registers a model the way OAP does at boot, without creating its tables.
     */
    Model register(final Class<?> storageClass, final int scopeId, final String name,
                   final DownSampling downSampling) throws Exception {
        try (MockedStatic<DefaultScopeDefine> ignored = mockStatic(DefaultScopeDefine.class)) {
            return models.add(
                storageClass, scopeId, new Storage(name, false, downSampling), StorageManipulationOpt.withSchemaChange());
        }
    }

    /**
     * Installs a model the way OAP does at boot, with a table for yesterday and today when it is split per day, and
     * tomorrow's, which OAP creates a day ahead ({@link JDBCHistoryDeleteDAO}), so a run that crosses midnight still
     * has a table for a row written at "now".
     */
    Model install(final Class<?> storageClass, final int scopeId, final String name,
                  final DownSampling downSampling) throws Exception {
        final Model model = register(storageClass, scopeId, name, downSampling);
        installer.isExists(model, StorageManipulationOpt.withSchemaChange());
        if (model.isTimeSeries()) {
            installer.createTable(model, day(MIDNIGHT - 1));
            installer.createTable(model, day(MIDNIGHT));
            installer.createTable(model, day(new DateTime(MIDNIGHT).plusDays(1).getMillis()));
        } else {
            installer.createTable(model);
        }
        return model;
    }

    JDBCTableInstaller installer() {
        return installer;
    }

    /**
     * Writes records through OAP's record DAO and its batch executor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeRecords(final Model model, final StorageBuilder<? extends Record> builder,
                      final List<? extends Record> records) throws Exception {
        final JDBCRecordDAO dao = new JDBCRecordDAO(client, (StorageBuilder<Record>) (StorageBuilder) builder);
        final List<PrepareRequest> requests = new ArrayList<>();
        for (final Record record : records) {
            requests.add(dao.prepareBatchInsert(model, record));
        }
        flush(requests);
    }

    /**
     * Writes metrics as new rows through OAP's metrics DAO and its batch executor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeMetrics(final Model model, final StorageBuilder<? extends Metrics> builder,
                      final List<? extends Metrics> metrics) throws Exception {
        final JDBCMetricsDAO dao = new JDBCMetricsDAO(client, (StorageBuilder<Metrics>) (StorageBuilder) builder);
        final List<PrepareRequest> requests = new ArrayList<>();
        for (final Metrics metric : metrics) {
            requests.add(dao.prepareBatchInsert(model, metric, (SessionCacheCallback) null));
        }
        flush(requests);
    }

    /**
     * Writes metrics again the way the metrics worker does when a stored row changes: an update of the row, in the
     * day table of the metric's time bucket, through OAP's metrics DAO and its batch executor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateMetrics(final Model model, final StorageBuilder<? extends Metrics> builder,
                       final List<? extends Metrics> metrics) throws Exception {
        final JDBCMetricsDAO dao = new JDBCMetricsDAO(client, (StorageBuilder<Metrics>) (StorageBuilder) builder);
        final List<PrepareRequest> requests = new ArrayList<>();
        for (final Metrics metric : metrics) {
            requests.add(dao.prepareBatchUpdate(model, metric, (SessionCacheCallback) null));
        }
        flush(requests);
    }

    /**
     * Writes a task row as {@link JDBCNoneStreamDAO} does, at once, into the table of the day it is created on.
     * {@code JDBCNoneStreamDAO} names that day "now"; the fixtures name it by the row's creation time, so a run that
     * crosses midnight still writes where its reads look.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeNoneStream(final Model model, final StorageBuilder<? extends NoneStream> builder,
                         final NoneStream row, final long createTime) throws Exception {
        try (Connection connection = client.getConnection()) {
            new JDBCSQLExecutor().getInsertExecutor(
                model, row, TimeBucket.getMinuteTimeBucket(createTime), (StorageBuilder<NoneStream>) (StorageBuilder) builder,
                new HashMapConverter.ToStorage(), null).invoke(connection);
        }
    }

    /**
     * Executes requests the way {@link JDBCBatchDAO#flush(List)} does: each statement with its additional ones,
     * grouped by SQL into one batch each.
     */
    void flush(final List<PrepareRequest> requests) throws Exception {
        final Map<PrepareRequest, List<PrepareRequest>> bySql = new LinkedHashMap<>();
        for (final PrepareRequest request : requests) {
            bySql.computeIfAbsent(request, sql -> new ArrayList<>()).add(request);
            final List<SQLExecutor> additional = ((SQLExecutor) request).getAdditionalSQLs();
            if (additional != null) {
                for (final SQLExecutor sql : additional) {
                    bySql.computeIfAbsent(sql, key -> new ArrayList<>()).add(sql);
                }
            }
        }
        for (final List<PrepareRequest> batch : bySql.values()) {
            new BatchSQLExecutor(client, batch).invoke(batch.size());
        }
    }

    JDBCMetricsQueryDAO metricsQueryDAO() {
        return target == Target.POSTGRESQL
            ? new PostgreSQLMetricsQueryDAO(client, tableHelper)
            : new JDBCMetricsQueryDAO(client, tableHelper);
    }

    JDBCAggregationQueryDAO aggregationQueryDAO() {
        return target == Target.POSTGRESQL
            ? new PostgreSQLAggregationQueryDAO(client, tableHelper)
            : new JDBCAggregationQueryDAO(client, tableHelper);
    }

    static long day(final long timestamp) {
        return TimeBucket.getTimeBucket(timestamp, DownSampling.Day);
    }

    /**
     * A query duration from {@code start} to {@code end}, as the query protocol writes it for {@code step}.
     */
    static Duration duration(final long start, final long end, final Step step) {
        final String pattern;
        switch (step) {
            case SECOND:
                pattern = "yyyy-MM-dd HHmmss";
                break;
            case MINUTE:
                pattern = "yyyy-MM-dd HHmm";
                break;
            case HOUR:
                pattern = "yyyy-MM-dd HH";
                break;
            default:
                pattern = "yyyy-MM-dd";
                break;
        }
        final Duration duration = new Duration();
        duration.setStep(step);
        duration.setStart(new DateTime(start).toString(pattern));
        duration.setEnd(new DateTime(end).toString(pattern));
        return duration;
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
