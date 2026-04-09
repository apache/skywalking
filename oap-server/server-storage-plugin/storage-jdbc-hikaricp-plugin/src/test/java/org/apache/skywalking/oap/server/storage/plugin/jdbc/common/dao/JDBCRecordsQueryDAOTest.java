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

import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JDBCRecordsQueryDAOTest {

    private static final String TABLE = "records_table";

    private Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart("2023-01-01 0000");
        duration.setEnd("2023-01-02 0000");
        duration.setStep(Step.MINUTE);
        return duration;
    }

    private RecordCondition buildCondition(String name, int topN, Order order) {
        final RecordCondition condition = new RecordCondition();
        condition.setName(name);
        condition.setTopN(topN);
        condition.setOrder(order);
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName("test-service");
        entity.setNormal(true);
        condition.setParentEntity(entity);
        return condition;
    }

    @Test
    void buildSQL_shouldContainTableColumnCondition() {
        final RecordCondition condition = buildCondition("top_n_db", 10, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = JDBCRecordsQueryDAO.buildSQL(condition, "value", duration, TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
    }

    @Test
    void buildSQL_shouldContainEntityIdAndTimeBucket() {
        final RecordCondition condition = buildCondition("top_n_db", 10, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = JDBCRecordsQueryDAO.buildSQL(condition, "value", duration, TABLE);

        assertThat(result.sql()).contains(TopN.ENTITY_ID + " = ?");
        assertThat(result.sql()).contains(TopN.TIME_BUCKET + " >= ?");
        assertThat(result.sql()).contains(TopN.TIME_BUCKET + " <= ?");
    }

    @Test
    void buildSQL_withOrderDES_shouldContainDesc() {
        final RecordCondition condition = buildCondition("top_n_db", 5, Order.DES);
        final Duration duration = buildDuration();

        final SQLAndParameters result = JDBCRecordsQueryDAO.buildSQL(condition, "latency", duration, TABLE);

        assertThat(result.sql()).contains("order by latency desc");
        assertThat(result.sql()).contains("limit 5");
    }

    @Test
    void buildSQL_withOrderASC_shouldContainAsc() {
        final RecordCondition condition = buildCondition("top_n_db", 5, Order.ASC);
        final Duration duration = buildDuration();

        final SQLAndParameters result = JDBCRecordsQueryDAO.buildSQL(condition, "latency", duration, TABLE);

        assertThat(result.sql()).contains("order by latency asc");
    }
}
