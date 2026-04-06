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

import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCLogQueryDAOTest {

    private static final String TABLE = "log_20260406";
    private static final String TAG_TABLE = "log_tag_20260406";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private TableHelper tableHelper;

    private JDBCLogQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCLogQueryDAO(jdbcClient, moduleManager, tableHelper);
    }

    @Test
    void buildSQL_shouldContainTableColumnConditionOnlyOnce() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        final long count = countOccurrences(sql, JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(count).as("TABLE_COLUMN condition should appear exactly once").isEqualTo(1);
    }

    @Test
    void buildSQL_withNoConditions_shouldProduceMinimalQuery() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("select * from " + TABLE);
        assertThat(sql).contains("where " + JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains("order by " + AbstractLogRecord.TIMESTAMP + " desc");
        assertThat(sql).contains("limit 10");
        assertThat(sql).doesNotContain("inner join");
    }

    @Test
    void buildSQL_withAscOrder_shouldProduceAscQuery() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.ASC, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("order by " + AbstractLogRecord.TIMESTAMP + " asc");
    }

    @Test
    void buildSQL_withServiceId_shouldIncludeServiceCondition() {
        final SQLAndParameters result = dao.buildSQL(
            "service-1", null, null, null, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + TABLE + "." + AbstractLogRecord.SERVICE_ID + " = ?");
        assertThat(result.parameters()).contains("service-1");
    }

    @Test
    void buildSQL_withServiceInstanceId_shouldIncludeInstanceCondition() {
        final SQLAndParameters result = dao.buildSQL(
            null, "instance-1", null, null, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AbstractLogRecord.SERVICE_INSTANCE_ID + " = ?");
        assertThat(result.parameters()).contains("instance-1");
    }

    @Test
    void buildSQL_withEndpointId_shouldIncludeEndpointCondition() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, "endpoint-1", null, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AbstractLogRecord.ENDPOINT_ID + " = ?");
        assertThat(result.parameters()).contains("endpoint-1");
    }

    @Test
    void buildSQL_withTraceId_shouldIncludeTraceCondition() {
        final TraceScopeCondition traceCondition = new TraceScopeCondition();
        traceCondition.setTraceId("trace-abc");

        final SQLAndParameters result = dao.buildSQL(
            null, null, null, traceCondition, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AbstractLogRecord.TRACE_ID + " = ?");
        assertThat(result.parameters()).contains("trace-abc");
    }

    @Test
    void buildSQL_withSegmentIdAndSpanId_shouldIncludeBothConditions() {
        final TraceScopeCondition traceCondition = new TraceScopeCondition();
        traceCondition.setSegmentId("segment-abc");
        traceCondition.setSpanId(1);

        final SQLAndParameters result = dao.buildSQL(
            null, null, null, traceCondition, Order.DES, 0, 10, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AbstractLogRecord.TRACE_SEGMENT_ID + " = ?");
        assertThat(sql).contains("and " + AbstractLogRecord.SPAN_ID + " = ?");
        assertThat(result.parameters()).contains("segment-abc");
        assertThat(result.parameters()).contains(1);
    }

    @Test
    void buildSQL_withSingleTag_shouldUseInnerJoin() {
        final List<Tag> tags = Collections.singletonList(new Tag("level", "ERROR"));

        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.DES, 0, 10, null, tags, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "0");
        assertThat(sql).contains(TAG_TABLE + "0." + AbstractLogRecord.TAGS + " = ?");
        assertThat(result.parameters()).contains("level=ERROR");
    }

    @Test
    void buildSQL_withMultipleTags_shouldUseMultipleInnerJoins() {
        final List<Tag> tags = Arrays.asList(new Tag("level", "ERROR"), new Tag("service", "order"));

        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.DES, 0, 10, null, tags, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "0");
        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "1");
        assertThat(sql).contains(TAG_TABLE + "0." + AbstractLogRecord.TAGS + " = ?");
        assertThat(sql).contains(TAG_TABLE + "1." + AbstractLogRecord.TAGS + " = ?");
    }

    @Test
    void buildSQL_withLimitAndOffset_shouldApplyTotalAsLimit() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, null, null, Order.DES, 5, 20, null, null, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("limit 25");
    }

    private long countOccurrences(final String text, final String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
