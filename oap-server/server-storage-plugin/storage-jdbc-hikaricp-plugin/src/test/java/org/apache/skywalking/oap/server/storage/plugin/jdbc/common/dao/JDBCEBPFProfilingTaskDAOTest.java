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

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCEBPFProfilingTaskDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCEBPFProfilingTaskDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCEBPFProfilingTaskDAO(jdbcClient, tableHelper);
    }

    @Test
    void getTaskRecord_shouldProduceValidSqlWithAndKeyword() throws Exception {
        when(tableHelper.getTablesWithinTTL(EBPFProfilingTaskRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("ebpf_profiling_task"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        final AtomicReference<Object[]> capturedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.set(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTaskRecord("task-abc-123");

        assertThat(capturedSql.get())
            .contains(" where ")
            .contains(" and ")
            .contains(EBPFProfilingTaskRecord.LOGICAL_ID + " = ?");
        assertThat(capturedParams.get()).contains("task-abc-123");
    }
}
