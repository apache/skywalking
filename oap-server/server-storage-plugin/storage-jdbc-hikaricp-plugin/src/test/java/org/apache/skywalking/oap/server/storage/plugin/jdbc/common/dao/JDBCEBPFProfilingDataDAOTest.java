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

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCEBPFProfilingDataDAOTest {

    private static final String TABLE = "ebpf_profiling_data";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCEBPFProfilingDataDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCEBPFProfilingDataDAO(jdbcClient, tableHelper);
    }

    @Test
    void buildSQL_shouldContainTableColumnCondition() {
        final SQLAndParameters result = dao.buildSQL(
            Arrays.asList("schedule-1"), 1000L, 2000L, TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.parameters()).contains(EBPFProfilingDataRecord.INDEX_NAME);
    }

    @Test
    void buildSQL_shouldContainScheduleIdInClause() {
        final SQLAndParameters result = dao.buildSQL(
            Arrays.asList("s1", "s2", "s3"), 1000L, 2000L, TABLE);

        assertThat(result.sql()).contains(EBPFProfilingDataRecord.SCHEDULE_ID + " in (?, ?, ?)");
    }

    @Test
    void buildSQL_shouldContainUploadTimeRange() {
        final SQLAndParameters result = dao.buildSQL(
            Arrays.asList("schedule-1"), 1000L, 2000L, TABLE);

        assertThat(result.sql()).contains(EBPFProfilingDataRecord.UPLOAD_TIME + ">=?");
        assertThat(result.sql()).contains(EBPFProfilingDataRecord.UPLOAD_TIME + "<?");
        assertThat(result.parameters()).contains(1000L);
        assertThat(result.parameters()).contains(2000L);
    }
}
