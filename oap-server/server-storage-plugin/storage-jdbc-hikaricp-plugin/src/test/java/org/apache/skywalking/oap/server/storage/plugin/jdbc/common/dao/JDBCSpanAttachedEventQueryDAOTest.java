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

import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
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

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
class JDBCSpanAttachedEventQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCSpanAttachedEventQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCSpanAttachedEventQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void queryZKSpanAttachedEvents_shouldContainTableColumnAndTraceIdInClause() throws Exception {
        when(tableHelper.getTablesWithinTTL(SpanAttachedEventRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("span_attached_event"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryZKSpanAttachedEvents(Arrays.asList("trace-1", "trace-2"), null);

        assertThat(capturedSql.get()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(capturedSql.get()).contains(SpanAttachedEventRecord.RELATED_TRACE_ID + " in (?,?)");
    }

    @Test
    void queryZKSpanAttachedEvents_shouldContainOrderByStartTime() throws Exception {
        when(tableHelper.getTablesWithinTTL(SpanAttachedEventRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("span_attached_event"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryZKSpanAttachedEvents(Arrays.asList("trace-1"), null);

        assertThat(capturedSql.get()).contains("order by " + SpanAttachedEventRecord.START_TIME_SECOND);
        assertThat(capturedSql.get()).contains(SpanAttachedEventRecord.START_TIME_NANOS);
    }

    @Test
    void querySWSpanAttachedEvents_shouldContainTableColumnAndTraceIdInClause() throws Exception {
        when(tableHelper.getTablesWithinTTL(SWSpanAttachedEventRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("sw_span_attached_event"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.querySWSpanAttachedEvents(Arrays.asList("trace-a", "trace-b", "trace-c"), null);

        assertThat(capturedSql.get()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(capturedSql.get()).contains(SWSpanAttachedEventRecord.RELATED_TRACE_ID + " in (?,?,?)");
    }

    @Test
    void queryZKSpanAttachedEvents_withDuration_shouldUseTablesForReadAndAddTimeBucketFilter() throws Exception {
        when(tableHelper.getTablesForRead(eq(SpanAttachedEventRecord.INDEX_NAME), anyLong(), anyLong()))
            .thenReturn(Collections.singletonList("span_attached_event_record_20260428"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryZKSpanAttachedEvents(Arrays.asList("trace-1"), buildDuration());

        assertThat(capturedSql.get()).contains(SpanAttachedEventRecord.TIME_BUCKET + " >= ?");
        assertThat(capturedSql.get()).contains(SpanAttachedEventRecord.TIME_BUCKET + " <= ?");
    }

    @Test
    void queryZKSpanAttachedEvents_withNullDuration_shouldFallBackToTablesWithinTTL() throws Exception {
        when(tableHelper.getTablesWithinTTL(SpanAttachedEventRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList("span_attached_event"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.queryZKSpanAttachedEvents(Arrays.asList("trace-1"), null);

        assertThat(capturedSql.get()).doesNotContain(SpanAttachedEventRecord.TIME_BUCKET + " >= ?");
    }

    @Test
    void querySWSpanAttachedEvents_withDuration_shouldUseTablesForReadAndAddTimeBucketFilter() throws Exception {
        when(tableHelper.getTablesForRead(eq(SWSpanAttachedEventRecord.INDEX_NAME), anyLong(), anyLong()))
            .thenReturn(Collections.singletonList("sw_span_attached_event_record_20260428"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.querySWSpanAttachedEvents(Arrays.asList("trace-1"), buildDuration());

        assertThat(capturedSql.get()).contains(SWSpanAttachedEventRecord.TIME_BUCKET + " >= ?");
        assertThat(capturedSql.get()).contains(SWSpanAttachedEventRecord.TIME_BUCKET + " <= ?");
    }

    private static Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart(new DateTime(2026, 4, 28, 14, 0).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(2026, 4, 28, 14, 30).toString("yyyy-MM-dd HHmm"));
        duration.setStep(Step.MINUTE);
        return duration;
    }
}
