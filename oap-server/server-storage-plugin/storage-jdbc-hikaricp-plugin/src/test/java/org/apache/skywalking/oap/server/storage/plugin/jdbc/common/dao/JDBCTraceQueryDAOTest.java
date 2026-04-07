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

import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCTraceQueryDAOTest {

    private static final String TABLE = "segment_20260406";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private TableHelper tableHelper;

    private JDBCTraceQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCTraceQueryDAO(moduleManager, jdbcClient, tableHelper);
    }

    @Test
    void queryByTraceId_shouldContainTableColumnAndTraceIdCondition() throws Exception {
        when(tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryByTraceId("trace-abc", null);

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains(SegmentRecord.TRACE_ID + " = ?");
        // TABLE_COLUMN should appear exactly once
        assertThat(countOccurrences(sql, JDBCTableInstaller.TABLE_COLUMN + " = ?")).isEqualTo(1);
    }

    @Test
    void queryBySegmentIdList_shouldUseInClause() throws Exception {
        when(tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryBySegmentIdList(Arrays.asList("seg-1", "seg-2", "seg-3"), null);

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains(SegmentRecord.SEGMENT_ID + " in (?,?,?)");
        assertThat(sql).doesNotContain(" or ");
    }

    @Test
    void queryByTraceIdWithInstanceId_shouldProduceValidSqlWithBothInClauses() throws Exception {
        when(tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryByTraceIdWithInstanceId(
            Arrays.asList("trace-1", "trace-2"),
            Arrays.asList("instance-1", "instance-2"),
            null
        );

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains(SegmentRecord.TRACE_ID + " in (?,?)");
        assertThat(sql).contains(" and " + SegmentRecord.SERVICE_INSTANCE_ID + " in (?,?)");
        // verify the IN clauses are both properly enclosed with parentheses
        assertThat(sql).containsPattern("trace_id in \\(\\?,\\?\\) and service_instance_id in \\(\\?,\\?\\)");
    }

    @Test
    void queryByTraceIdWithInstanceId_withSingleItems_shouldProduceValidSql() throws Exception {
        when(tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryByTraceIdWithInstanceId(
            Collections.singletonList("trace-1"),
            Collections.singletonList("instance-1"),
            null
        );

        final String sql = capturedSql.get();
        assertThat(sql).contains(SegmentRecord.TRACE_ID + " in (?)");
        assertThat(sql).contains(" and " + SegmentRecord.SERVICE_INSTANCE_ID + " in (?)");
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
