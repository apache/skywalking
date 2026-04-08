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

import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.joda.time.DateTime;
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
class JDBCBrowserLogQueryDAOTest {

    private static final String TABLE = BrowserErrorLogRecord.INDEX_NAME;

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCBrowserLogQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCBrowserLogQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void buildSQL_shouldAlwaysContainTableColumnCondition() {
        final SQLAndParameters result = dao.buildSQL(null, null, null, null, null, 10, 0, TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.parameters()).contains(BrowserErrorLogRecord.INDEX_NAME);
    }

    @Test
    void buildSQL_withNoOptionalConditions_shouldProduceMinimalSQL() {
        final SQLAndParameters result = dao.buildSQL(null, null, null, null, null, 10, 0, TABLE);

        assertThat(result.sql()).doesNotContain(BrowserErrorLogRecord.SERVICE_ID);
        assertThat(result.sql()).doesNotContain(BrowserErrorLogRecord.SERVICE_VERSION_ID);
        assertThat(result.sql()).doesNotContain(BrowserErrorLogRecord.PAGE_PATH_ID);
        assertThat(result.sql()).doesNotContain(BrowserErrorLogRecord.ERROR_CATEGORY);
        assertThat(result.sql()).doesNotContain(BrowserErrorLogRecord.TIME_BUCKET);
    }

    @Test
    void buildSQL_withServiceId_shouldIncludeServiceCondition() {
        final SQLAndParameters result = dao.buildSQL("service-1", null, null, null, null, 10, 0, TABLE);

        assertThat(result.sql()).contains(BrowserErrorLogRecord.SERVICE_ID + " = ?");
        assertThat(result.parameters()).contains("service-1");
    }

    @Test
    void buildSQL_withServiceVersionId_shouldIncludeVersionCondition() {
        final SQLAndParameters result = dao.buildSQL(null, "version-1", null, null, null, 10, 0, TABLE);

        assertThat(result.sql()).contains(BrowserErrorLogRecord.SERVICE_VERSION_ID + " = ?");
        assertThat(result.parameters()).contains("version-1");
    }

    @Test
    void buildSQL_withPagePathId_shouldIncludePagePathCondition() {
        final SQLAndParameters result = dao.buildSQL(null, null, "path-1", null, null, 10, 0, TABLE);

        assertThat(result.sql()).contains(BrowserErrorLogRecord.PAGE_PATH_ID + " = ?");
        assertThat(result.parameters()).contains("path-1");
    }

    @Test
    void buildSQL_withCategory_shouldIncludeCategoryCondition() {
        final SQLAndParameters result = dao.buildSQL(
            null, null, null, BrowserErrorCategory.AJAX, null, 10, 0, TABLE);

        assertThat(result.sql()).contains(BrowserErrorLogRecord.ERROR_CATEGORY + " = ?");
        assertThat(result.parameters()).contains(BrowserErrorCategory.AJAX.getValue());
    }

    @Test
    void buildSQL_withDuration_shouldIncludeTimeBucketRange() {
        final Duration duration = new Duration();
        duration.setStart(new DateTime(2023, 1, 1, 0, 0).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(2023, 1, 2, 0, 0).toString("yyyy-MM-dd HHmm"));
        duration.setStep(Step.MINUTE);

        final SQLAndParameters result = dao.buildSQL(null, null, null, null, duration, 10, 0, TABLE);

        assertThat(result.sql()).contains(BrowserErrorLogRecord.TIME_BUCKET + " >= ?");
        assertThat(result.sql()).contains(BrowserErrorLogRecord.TIME_BUCKET + " <= ?");
    }

    @Test
    void buildSQL_limitAndOffset_shouldBeApplied() {
        final SQLAndParameters result = dao.buildSQL(null, null, null, null, null, 20, 5, TABLE);

        assertThat(result.sql()).contains("limit 25");
    }
}
