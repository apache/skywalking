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

import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.input.Duration;
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
class JDBCAlarmQueryDAOTest {

    private static final String TABLE = "alarm_record_20260406";
    private static final String TAG_TABLE = "alarm_record_tag_20260406";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private TableHelper tableHelper;

    private JDBCAlarmQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCAlarmQueryDAO(jdbcClient, moduleManager, tableHelper);
    }

    @Test
    void buildSQL_shouldContainTableColumnConditionOnlyOnce() {
        final SQLAndParameters result = dao.buildSQL(null, null, 10, 0, null, null, TABLE);
        final String sql = result.sql();

        final long count = countOccurrences(sql, JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(count).as("TABLE_COLUMN condition should appear exactly once").isEqualTo(1);
    }

    @Test
    void buildSQL_withNoConditions_shouldProduceMinimalQuery() {
        final SQLAndParameters result = dao.buildSQL(null, null, 10, 0, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("select * from " + TABLE);
        assertThat(sql).contains("where " + TABLE + "." + JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains("order by " + AlarmRecord.START_TIME + " desc");
        assertThat(sql).contains("limit 10");
        assertThat(sql).doesNotContain("inner join");
        assertThat(sql).doesNotContain(AlarmRecord.SCOPE);
        assertThat(sql).doesNotContain("like");
    }

    @Test
    void buildSQL_withScopeId_shouldIncludeScopeCondition() {
        final SQLAndParameters result = dao.buildSQL(1, null, 10, 0, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AlarmRecord.SCOPE + " = ?");
        assertThat(result.parameters()).contains(1);
    }

    @Test
    void buildSQL_withKeyword_shouldIncludeLikeCondition() {
        final SQLAndParameters result = dao.buildSQL(null, "error", 10, 0, null, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + AlarmRecord.ALARM_MESSAGE + " like concat('%',?,'%')");
        assertThat(result.parameters()).contains("error");
    }

    @Test
    void buildSQL_withDuration_shouldIncludeTimeBucketConditions() {
        final Duration duration = new Duration();
        duration.setStart("2026-04-06 0000");
        duration.setEnd("2026-04-06 2359");
        duration.setStep(org.apache.skywalking.oap.server.core.query.enumeration.Step.MINUTE);

        final SQLAndParameters result = dao.buildSQL(null, null, 10, 0, duration, null, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("and " + TABLE + "." + AlarmRecord.TIME_BUCKET + " >= ?");
        assertThat(sql).contains("and " + TABLE + "." + AlarmRecord.TIME_BUCKET + " <= ?");
    }

    @Test
    void buildSQL_withSingleTag_shouldUseInnerJoin() {
        final List<Tag> tags = Collections.singletonList(new Tag("env", "prod"));

        final SQLAndParameters result = dao.buildSQL(null, null, 10, 0, null, tags, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "0");
        assertThat(sql).contains(TAG_TABLE + "0." + AlarmRecord.TAGS + " = ?");
        assertThat(result.parameters()).contains("env=prod");
    }

    @Test
    void buildSQL_withMultipleTags_shouldUseMultipleInnerJoins() {
        final List<Tag> tags = Arrays.asList(new Tag("env", "prod"), new Tag("region", "us-east"));

        final SQLAndParameters result = dao.buildSQL(null, null, 10, 0, null, tags, TABLE);
        final String sql = result.sql();

        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "0");
        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "1");
        assertThat(sql).contains(TAG_TABLE + "0." + AlarmRecord.TAGS + " = ?");
        assertThat(sql).contains(TAG_TABLE + "1." + AlarmRecord.TAGS + " = ?");
    }

    @Test
    void buildSQL_withLimitAndOffset_shouldApplyTotalAsLimit() {
        final SQLAndParameters result = dao.buildSQL(null, null, 20, 5, null, null, TABLE);
        final String sql = result.sql();

        // JDBC uses offset+limit as the database LIMIT, then skips in application
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
