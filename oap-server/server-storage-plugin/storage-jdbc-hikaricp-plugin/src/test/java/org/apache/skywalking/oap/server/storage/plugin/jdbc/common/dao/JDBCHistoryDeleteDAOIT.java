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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseExtension;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class JDBCHistoryDeleteDAOIT {
    @Container
    private final PostgreSQLContainer<?> psqlContainer = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("skywalking")
        .withUsername("postgres")
        .withPassword("123456");

    private JDBCClient jdbcClient;

    @Mock
    private ModuleManager moduleManager;
    private Model model;
    private TableHelper tableHelper;
    private JDBCTableInstaller tableInstaller;

    @BeforeEach
    void setup() {
        final var properties = new Properties();
        properties.setProperty("jdbcUrl", psqlContainer.getJdbcUrl());
        properties.setProperty("dataSource.user", psqlContainer.getUsername());
        properties.setProperty("dataSource.password", psqlContainer.getPassword());

        jdbcClient = new JDBCClient(properties);
        jdbcClient.connect();

        tableHelper = new TableHelper(moduleManager, jdbcClient);
        tableInstaller = new JDBCTableInstaller(jdbcClient, moduleManager);

        final var providerHolder = mock(ModuleProviderHolder.class);
        final var coreModule = mock(CoreModuleProvider.class);
        final var configService = mock(ConfigService.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(coreModule);
        when(coreModule.getService(ConfigService.class)).thenReturn(configService);
        when(configService.getMetricsDataTTL()).thenReturn(3);

        final var serviceTrafficNameColumn = mock(Column.class);
        when(serviceTrafficNameColumn.name()).thenReturn("service_traffic_name");
        final var timeBucketColumn = mock(Column.class);
        when(timeBucketColumn.name()).thenReturn("time_bucket");

        model = new Model(
            "service_traffic",
            Arrays.asList(
                new ModelColumn(new ColumnName(serviceTrafficNameColumn), String.class, String.class, false, false, false, 100,
                    new SQLDatabaseExtension(), null, null),
                new ModelColumn(new ColumnName(timeBucketColumn), Long.class, Long.class, false, false, false, 0,
                    new SQLDatabaseExtension(), null, null)
            ), 1, DownSampling.Minute, false, ServiceTraffic.class, false, new SQLDatabaseModelExtension(),
            new BanyanDBModelExtension(), new ElasticSearchModelExtension());

        TableMetaInfo.addModel(model);
    }

    @Test
    void test() throws SQLException {
        // Table install should create the table.
        var clock = Clock.fixed(Instant.parse("2023-03-17T10:00:00Z"), ZoneId.systemDefault());
        tableInstaller.createTable(model, 20230317);

        var jdbcHistoryDeleteDAO = new JDBCHistoryDeleteDAO(jdbcClient, tableHelper, tableInstaller, clock);
        jdbcHistoryDeleteDAO.deleteHistory(model, "time_bucket", 3);
        try (final var conn = jdbcClient.getConnection();
             final var rs = conn.getMetaData().getTables(conn.getCatalog(), null, "service_traffic_20230317", null)) {
            assertThat(rs.next()).isTrue();
        }

        // HistoryDeleteDAO should delete the table out of TTL.
        clock = Clock.fixed(Instant.parse("2023-03-21T10:00:00Z"), ZoneId.systemDefault());
        jdbcHistoryDeleteDAO = new JDBCHistoryDeleteDAO(jdbcClient, tableHelper, tableInstaller, clock);
        jdbcHistoryDeleteDAO.deleteHistory(model, "time_bucket", 3);
        try (final var conn = jdbcClient.getConnection();
             final var rs = conn.getMetaData().getTables(conn.getCatalog(), null, "service_traffic_20230317", null)) {
            assertThat(rs.next()).isFalse();
        }
        // ... and should create the new table
        try (final var conn = jdbcClient.getConnection();
             final var rs = conn.getMetaData().getTables(conn.getCatalog(), null, "service_traffic_20230322", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
