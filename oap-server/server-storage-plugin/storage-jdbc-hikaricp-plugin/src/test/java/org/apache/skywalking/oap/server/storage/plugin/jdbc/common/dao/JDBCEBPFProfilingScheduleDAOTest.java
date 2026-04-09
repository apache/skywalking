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

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCEBPFProfilingScheduleDAOTest {

    private static final String TABLE = "ebpf_profiling_schedule";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCEBPFProfilingScheduleDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCEBPFProfilingScheduleDAO(jdbcClient, tableHelper);
    }

    @Test
    void buildSQL_shouldContainTableColumnCondition() {
        final SQLAndParameters result = dao.buildSQL("task-1", TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.parameters()).contains(EBPFProfilingScheduleRecord.INDEX_NAME);
    }

    @Test
    void buildSQL_shouldContainTaskIdCondition() {
        final SQLAndParameters result = dao.buildSQL("task-abc", TABLE);

        assertThat(result.sql()).contains(EBPFProfilingScheduleRecord.TASK_ID + "=?");
        assertThat(result.parameters()).contains("task-abc");
    }

    @Test
    void buildSQL_shouldContainWhereClause() {
        final SQLAndParameters result = dao.buildSQL("task-1", TABLE);

        assertThat(result.sql()).contains(" where ");
    }
}
