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

import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.joda.time.DateTime;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCZipkinQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCZipkinQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCZipkinQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void getTraces_shouldUseInClauseForMultipleTraceIds() throws Exception {
        when(tableHelper.getTablesWithinTTL(ZipkinSpanRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("zipkin_span"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        final AtomicReference<Object[]> capturedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.set(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        final Set<String> traceIds = new LinkedHashSet<>(Arrays.asList("abc123", "def456", "ghi789"));
        dao.getTraces(traceIds, null);

        final String sql = capturedSql.get();
        assertThat(sql).contains(ZipkinSpanRecord.TRACE_ID + " in (?,?,?)");
        assertThat(sql).doesNotContain(" or ");

        assertThat(capturedParams.get())
            .contains("abc123", "def456", "ghi789");
    }

    @Test
    void getTraces_shouldReturnEmptyListWhenTraceIdsEmpty() throws Exception {
        final Set<String> traceIds = Collections.emptySet();
        assertThat(dao.getTraces(traceIds, null)).isEmpty();
    }

    @Test
    void getTraces_singleTraceIdShouldProduceInClauseWithOnePlaceholder() throws Exception {
        when(tableHelper.getTablesWithinTTL(ZipkinSpanRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("zipkin_span"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        final Set<String> traceIds = Collections.singleton("abc123");
        dao.getTraces(traceIds, null);

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).contains(ZipkinSpanRecord.TRACE_ID + " in (?)");
    }

    @Test
    void getTrace_withDuration_shouldUseTablesForReadAndAddTimeBucketFilter() throws Exception {
        when(tableHelper.getTablesForRead(eq(ZipkinSpanRecord.INDEX_NAME), anyLong(), anyLong()))
            .thenReturn(Collections.singletonList("zipkin_span_record_20260428"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTrace("trace-abc", buildDuration());

        final String sql = capturedSql.get();
        assertThat(sql).contains(ZipkinSpanRecord.TRACE_ID + " = ?");
        assertThat(sql).contains(ZipkinSpanRecord.TIME_BUCKET + " >= ?");
        assertThat(sql).contains(ZipkinSpanRecord.TIME_BUCKET + " <= ?");
    }

    @Test
    void getTrace_withNullDuration_shouldFallBackToTablesWithinTTL() throws Exception {
        when(tableHelper.getTablesWithinTTL(ZipkinSpanRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("zipkin_span_record"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.getTrace("trace-abc", null);

        final String sql = capturedSql.get();
        assertThat(sql).doesNotContain(ZipkinSpanRecord.TIME_BUCKET + " >= ?");
    }

    private static Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart(new DateTime(2026, 4, 28, 14, 0).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(2026, 4, 28, 14, 30).toString("yyyy-MM-dd HHmm"));
        duration.setStep(Step.MINUTE);
        return duration;
    }
}
