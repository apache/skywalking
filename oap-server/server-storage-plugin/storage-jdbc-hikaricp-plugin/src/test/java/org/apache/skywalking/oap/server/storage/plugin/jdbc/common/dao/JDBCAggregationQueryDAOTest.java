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
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCAggregationQueryDAOTest {

    private static final String TABLE = "metrics_table";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCAggregationQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCAggregationQueryDAO(jdbcClient, tableHelper);
    }

    private TopNCondition buildCondition(String name, int topN, Order order) {
        final TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setTopN(topN);
        condition.setOrder(order);
        return condition;
    }

    private Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart("2023-01-01 0000");
        duration.setEnd("2023-01-02 0000");
        duration.setStep(Step.MINUTE);
        return duration;
    }

    @Test
    void buildSQL_shouldContainTableColumnAndTimeBucketRange() {
        final TopNCondition condition = buildCondition("service_resp_time", 10, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQL(condition, "value", duration, null, TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.sql()).contains(Metrics.TIME_BUCKET + " >= ?");
        assertThat(result.sql()).contains(Metrics.TIME_BUCKET + " <= ?");
    }

    @Test
    void buildSQL_shouldContainSubqueryWithGroupBy() {
        final TopNCondition condition = buildCondition("service_resp_time", 10, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQL(condition, "value", duration, null, TABLE);

        assertThat(result.sql()).contains("avg(value) as result");
        assertThat(result.sql()).contains("group by " + Metrics.ENTITY_ID);
        assertThat(result.sql()).contains("as T order by result");
    }

    @Test
    void buildSQL_withOrderDES_shouldContainDesc() {
        final TopNCondition condition = buildCondition("service_resp_time", 5, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQL(condition, "value", duration, null, TABLE);

        assertThat(result.sql()).contains("order by result desc");
        assertThat(result.sql()).contains("limit 5");
    }

    @Test
    void buildSQL_withOrderASC_shouldContainAsc() {
        final TopNCondition condition = buildCondition("service_resp_time", 5, Order.ASC);
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQL(condition, "value", duration, null, TABLE);

        assertThat(result.sql()).contains("order by result asc");
    }

    @Test
    void buildSQL_withAdditionalConditions_shouldAppendConditions() {
        final TopNCondition condition = buildCondition("service_resp_time", 10, Order.DES);
        final Duration duration = buildDuration();
        final KeyValue kv = new KeyValue("service_id", "svc-1");

        final SQLAndParameters result = dao.buildSQL(
            condition, "value", duration, Collections.singletonList(kv), TABLE);

        assertThat(result.sql()).contains("service_id = ?");
        assertThat(result.parameters()).contains("svc-1");
    }

    /**
     * A {@code doubleAvg} metric such as {@code instance_jvm_cpu} stores fractional values, so each day table's
     * summation is fractional: the mean of 1.9 and 2.9 over two days is 2, not the 1 of truncating each day first.
     */
    @Test
    void sortMetrics_shouldMergeFractionalSummationsOfEveryDayBeforeTheMean() throws Exception {
        when(tableHelper.getTablesForRead(anyString(), anyLong(), anyLong()))
            .thenReturn(Arrays.asList("instance_jvm_cpu_20230101", "instance_jvm_cpu_20230102"));
        final Iterator<ResultSet> days = Arrays.asList(
            summations(new String[] {"instance-1"}, "1.9"), summations(new String[] {"instance-1"}, "2.9")).iterator();
        doAnswer(invocation -> invocation.<JDBCClient.ResultHandler<Object>>getArgument(1).handle(days.next()))
            .when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        final List<SelectedRecord> top = dao.sortMetrics(
            buildCondition("instance_jvm_cpu", 10, Order.DES), "value", buildDuration(), null);

        assertThat(top).hasSize(1);
        assertThat(top.get(0).getId()).isEqualTo("instance-1");
        assertThat(top.get(0).getValue()).isEqualTo("2");
    }

    /**
     * Two idle instances, 0.1 and 0.9 on both days: their means truncate to the same 0, yet the ranking keeps them
     * apart, as the database ranks {@code avg()}.
     */
    @Test
    void sortMetrics_shouldRankByTheExactMeanBeforeTruncatingIt() throws Exception {
        when(tableHelper.getTablesForRead(anyString(), anyLong(), anyLong()))
            .thenReturn(Arrays.asList("instance_jvm_cpu_20230101", "instance_jvm_cpu_20230102"));
        final String[] instances = {"instance-1", "instance-2"};
        // Two day tables for each of the two queries below.
        final Iterator<ResultSet> days = Arrays.asList(
            summations(instances, "0.1", "0.9"), summations(instances, "0.1", "0.9"),
            summations(instances, "0.1", "0.9"), summations(instances, "0.1", "0.9")).iterator();
        doAnswer(invocation -> invocation.<JDBCClient.ResultHandler<Object>>getArgument(1).handle(days.next()))
            .when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        final List<SelectedRecord> busiest = dao.sortMetrics(
            buildCondition("instance_jvm_cpu", 1, Order.DES), "value", buildDuration(), null);
        assertThat(busiest).extracting(SelectedRecord::getId).containsExactly("instance-2");
        assertThat(busiest).extracting(SelectedRecord::getValue).containsExactly("0");

        final List<SelectedRecord> idlest = dao.sortMetrics(
            buildCondition("instance_jvm_cpu", 1, Order.ASC), "value", buildDuration(), null);
        assertThat(idlest).extracting(SelectedRecord::getId).containsExactly("instance-1");
    }

    /**
     * One day table's summation and sample count per entity, one sample each.
     */
    private static ResultSet summations(final String[] entityIds, final String... summations) throws Exception {
        final ResultSet resultSet = mock(ResultSet.class);
        // A row per entity: true for each, then false.
        final Boolean[] afterFirst = new Boolean[entityIds.length];
        Arrays.fill(afterFirst, true);
        afterFirst[entityIds.length - 1] = false;
        when(resultSet.next()).thenReturn(true, afterFirst);
        when(resultSet.getString(Metrics.ENTITY_ID))
            .thenReturn(entityIds[0], Arrays.copyOfRange(entityIds, 1, entityIds.length));
        final BigDecimal[] values = Arrays.stream(summations).map(BigDecimal::new).toArray(BigDecimal[]::new);
        when(resultSet.getBigDecimal("summation")).thenReturn(values[0], Arrays.copyOfRange(values, 1, values.length));
        when(resultSet.getLong("samples")).thenReturn(1L);
        return resultSet;
    }

}
