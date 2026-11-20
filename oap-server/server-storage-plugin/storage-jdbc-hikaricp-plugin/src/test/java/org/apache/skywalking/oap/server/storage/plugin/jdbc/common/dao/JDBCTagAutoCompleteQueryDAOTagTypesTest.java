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

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Several tag types are read in one query: the tags of any of them, within one limit.
 */
class JDBCTagAutoCompleteQueryDAOTagTypesTest {
    private static final List<String> LOG_SOURCES = List.of(TagType.LOG.name(), TagType.OTLP_LOG.name());

    private final JDBCTagAutoCompleteQueryDAO dao =
        new JDBCTagAutoCompleteQueryDAO(mock(JDBCClient.class), mock(TableHelper.class));

    @Test
    void theKeysOfSeveralTagTypesAreOneQuery() {
        final SQLAndParameters result = dao.buildSQLForQueryKeys(LOG_SOURCES, 100, duration(), "t");

        assertThat(result.sql()).contains(TagAutocompleteData.TAG_TYPE + " in (?,?)");
        assertThat(result.sql()).contains("limit 100");
        assertThat(result.parameters()).containsSubsequence(TagType.LOG.name(), TagType.OTLP_LOG.name());
    }

    @Test
    void theValuesOfSeveralTagTypesAreOneQuery() {
        final SQLAndParameters result = dao.buildSQLForQueryValues(LOG_SOURCES, "level", 100, duration(), "t");

        assertThat(result.sql()).contains(TagAutocompleteData.TAG_TYPE + " in (?,?)");
        assertThat(result.sql()).contains("select distinct " + TagAutocompleteData.TAG_VALUE + " from ");
        assertThat(result.sql()).endsWith(" limit 100");
        assertThat(result.parameters()).containsSubsequence("level", TagType.LOG.name(), TagType.OTLP_LOG.name());
    }

    private static Duration duration() {
        final Duration duration = new Duration();
        duration.setStart("2023-01-01 0000");
        duration.setEnd("2023-01-02 0000");
        duration.setStep(Step.MINUTE);
        return duration;
    }
}
