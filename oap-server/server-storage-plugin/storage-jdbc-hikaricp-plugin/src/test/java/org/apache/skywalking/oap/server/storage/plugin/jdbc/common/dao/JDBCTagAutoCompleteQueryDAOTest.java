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

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
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
class JDBCTagAutoCompleteQueryDAOTest {

    private static final String TABLE = "tag_autocomplete_table";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCTagAutoCompleteQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCTagAutoCompleteQueryDAO(jdbcClient, tableHelper);
    }

    private Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart("2023-01-01 0000");
        duration.setEnd("2023-01-02 0000");
        duration.setStep(Step.MINUTE);
        return duration;
    }

    @Test
    void buildSQLForQueryKeys_shouldContainDistinctAndTagType() {
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQLForQueryKeys(TagType.TRACE, 100, duration, TABLE);

        assertThat(result.sql()).contains("select distinct " + TagAutocompleteData.TAG_KEY);
        assertThat(result.sql()).contains(TagAutocompleteData.TAG_TYPE + " = ?");
        assertThat(result.parameters()).contains(TagType.TRACE.name());
    }

    @Test
    void buildSQLForQueryKeys_shouldContainLimit() {
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQLForQueryKeys(TagType.TRACE, 50, duration, TABLE);

        assertThat(result.sql()).contains("limit 50");
    }

    @Test
    void buildSQLForQueryValues_shouldContainTagKeyCondition() {
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQLForQueryValues(
            TagType.TRACE, "http.method", 100, duration, TABLE);

        assertThat(result.sql()).contains(TagAutocompleteData.TAG_KEY + " = ?");
        assertThat(result.parameters()).contains("http.method");
    }

    @Test
    void buildSQLForQueryValues_shouldContainTagTypeCondition() {
        final Duration duration = buildDuration();

        final SQLAndParameters result = dao.buildSQLForQueryValues(
            TagType.LOG, "level", 100, duration, TABLE);

        assertThat(result.sql()).contains(TagAutocompleteData.TAG_TYPE + " = ?");
        assertThat(result.parameters()).contains(TagType.LOG.name());
    }
}
