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

import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCJFRDataQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCJFRDataQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCJFRDataQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void getByTaskIdAndInstancesAndEvent_shouldFilterByTaskId() throws Exception {
        when(tableHelper.getTablesWithinTTL(JFRProfilingDataRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("jfr_profiling_data"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        final AtomicReference<Object[]> capturedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.set(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getByTaskIdAndInstancesAndEvent("task1", Collections.emptyList(), "CPU");

        assertThat(capturedSql.get()).contains(JFRProfilingDataRecord.TASK_ID + " = ?");
        assertThat(capturedParams.get()).contains("task1");
    }

    @Test
    void getByTaskIdAndInstancesAndEvent_shouldFilterByInstanceIds() throws Exception {
        when(tableHelper.getTablesWithinTTL(JFRProfilingDataRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("jfr_profiling_data"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getByTaskIdAndInstancesAndEvent("task1", Arrays.asList("inst1", "inst2", "inst3"), "CPU");

        assertThat(capturedSql.get()).contains(JFRProfilingDataRecord.INSTANCE_ID + " in (?,?,?)");
    }

    @Test
    void getByTaskIdAndInstancesAndEvent_shouldReturnEmptyListWhenTaskIdIsBlank() throws Exception {
        final List<JFRProfilingDataRecord> result =
            dao.getByTaskIdAndInstancesAndEvent("", Arrays.asList("inst1"), "CPU");

        assertThat(result).isEmpty();
    }
}
