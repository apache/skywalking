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

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCMetricsQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCMetricsQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCMetricsQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void buildMetricsValueSql_shouldContainSelectWithAggAndEntityId() {
        final StringBuilder sql = dao.buildMetricsValueSql("avg", "value", "metrics_table");

        assertThat(sql.toString()).contains("select " + Metrics.ENTITY_ID + " id");
        assertThat(sql.toString()).contains("avg(value) result");
        assertThat(sql.toString()).contains("from metrics_table");
        assertThat(sql.toString()).contains("where");
    }

    @Test
    void buildMetricsValueSql_withDifferentOp_shouldReflectInSQL() {
        final StringBuilder sql = dao.buildMetricsValueSql("sum", "latency", "service_metrics");

        assertThat(sql.toString()).contains("sum(latency) result");
        assertThat(sql.toString()).contains("from service_metrics");
    }

    @Test
    void readLabeledMetricsValuesWithoutEntity_shouldContainTableColumnAndTimeBucket() throws Exception {
        when(tableHelper.getTablesForRead(anyString(), any(Long.class), any(Long.class)))
            .thenReturn(Collections.singletonList("metrics_table"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return null;
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        try {
            dao.readLabeledMetricsValuesWithoutEntity(
                "service_resp_time", "value", Collections.emptyList(),
                buildDuration());
        } catch (Exception ignored) {
            // ValueColumnMetadata not initialized, but SQL is already captured
        }

        assertThat(capturedSql.get()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(capturedSql.get()).contains(Metrics.TIME_BUCKET + " >= ?");
        assertThat(capturedSql.get()).contains(Metrics.TIME_BUCKET + " <= ?");
        assertThat(capturedSql.get()).contains("limit");
    }

    private org.apache.skywalking.oap.server.core.query.input.Duration buildDuration() {
        final var duration = new org.apache.skywalking.oap.server.core.query.input.Duration();
        duration.setStart("2023-01-01 0000");
        duration.setEnd("2023-01-02 0000");
        duration.setStep(org.apache.skywalking.oap.server.core.query.enumeration.Step.MINUTE);
        return duration;
    }
}
