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

import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCAsyncProfilerTaskQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCAsyncProfilerTaskQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCAsyncProfilerTaskQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void getTaskList_withServiceId_shouldIncludeServiceIdFilter() throws Exception {
        when(tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("async_profiler_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTaskList("service-1", null, null, null);

        assertThat(capturedSql.get()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(capturedSql.get()).contains(AsyncProfilerTaskRecord.SERVICE_ID + "=?");
    }

    @Test
    void getTaskList_withoutServiceId_shouldNotIncludeServiceIdFilter() throws Exception {
        when(tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("async_profiler_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTaskList(null, null, null, null);

        assertThat(capturedSql.get()).doesNotContain(AsyncProfilerTaskRecord.SERVICE_ID);
    }

    @Test
    void getTaskList_shouldAlwaysOrderByCreateTimeDesc() throws Exception {
        when(tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("async_profiler_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTaskList(null, null, null, null);

        assertThat(capturedSql.get()).contains("ORDER BY " + AsyncProfilerTaskRecord.CREATE_TIME + " DESC");
    }

    @Test
    void getTaskList_withLimit_shouldIncludeLimitClause() throws Exception {
        when(tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("async_profiler_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTaskList(null, null, null, 10);

        assertThat(capturedSql.get()).contains("LIMIT 10");
    }

    @Test
    void getById_shouldContainTableColumnAndTaskIdWithLimit() throws Exception {
        when(tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("async_profiler_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return null;
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getById("task-id-1");

        assertThat(capturedSql.get()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(capturedSql.get()).contains(AsyncProfilerTaskRecord.TASK_ID + "=?");
        assertThat(capturedSql.get()).contains("LIMIT 1");
    }
}
