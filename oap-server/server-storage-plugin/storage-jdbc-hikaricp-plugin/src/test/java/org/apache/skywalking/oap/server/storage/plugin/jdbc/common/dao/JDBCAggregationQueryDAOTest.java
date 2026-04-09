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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

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
}
