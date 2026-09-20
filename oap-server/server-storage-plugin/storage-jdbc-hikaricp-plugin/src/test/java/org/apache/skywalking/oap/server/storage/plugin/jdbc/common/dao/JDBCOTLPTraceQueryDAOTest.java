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
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCOTLPTraceQueryDAOTest {
    private static final String SPAN_TABLE = OTLPSpanRecord.INDEX_NAME + "_20260917";
    private static final String TAG_TABLE = OTLPSpanRecord.ADDITIONAL_TAG_TABLE + "_20260917";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCOTLPTraceQueryDAO dao;
    private final List<String> capturedSql = new ArrayList<>();
    private final List<Object[]> capturedParams = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        dao = new JDBCOTLPTraceQueryDAO(jdbcClient, tableHelper);
        when(tableHelper.getTablesWithinTTL(OTLPSpanRecord.INDEX_NAME))
            .thenReturn(Collections.singletonList(SPAN_TABLE));
        doAnswer(invocation -> {
            capturedSql.add(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.add(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return null;
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));
    }

    @Test
    void queryTraces_shouldJoinTheTagTableOncePerTagAndGroupByTrace() throws Exception {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setServiceName("checkout");
        condition.setKind(2);
        condition.setStatusCode(2);
        condition.setMinDurationNanos(1_000_000L);
        condition.setTags(Arrays.asList(new Tag("http.request.method", "GET"), new Tag("url.path", "/cart")));

        dao.queryTraces(condition);

        assertThat(capturedSql).hasSize(1);
        final String sql = capturedSql.get(0);
        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "0 on " + SPAN_TABLE + "."
                                     + JDBCTableInstaller.ID_COLUMN + " = " + TAG_TABLE + "0." + JDBCTableInstaller.ID_COLUMN);
        assertThat(sql).contains("inner join " + TAG_TABLE + " " + TAG_TABLE + "1 on ");
        assertThat(sql).contains(TAG_TABLE + "0." + OTLPSpanRecord.TAGS + " = ?");
        assertThat(sql).contains(TAG_TABLE + "1." + OTLPSpanRecord.TAGS + " = ?");
        assertThat(sql).contains(SPAN_TABLE + "." + OTLPSpanRecord.SERVICE_NAME + " = ?");
        assertThat(sql).contains(OTLPSpanRecord.KIND + " = ?");
        assertThat(sql).contains(OTLPSpanRecord.STATUS_CODE + " = ?");
        assertThat(sql).contains(OTLPSpanRecord.DURATION + " >= ?");
        assertThat(sql).doesNotContain(OTLPSpanRecord.DURATION + " <= ?");
        assertThat(sql).endsWith("group by " + SPAN_TABLE + "." + OTLPSpanRecord.TRACE_ID
                                     + " order by min(" + OTLPSpanRecord.START_TIME + ") desc limit 20");
        assertThat(capturedParams.get(0)).containsExactly(
            OTLPSpanRecord.INDEX_NAME, "checkout", 2, 2, 1_000_000L, "http.request.method=GET", "url.path=/cart");
    }

    @Test
    void queryTraces_shouldOrderByTheLongestSpanWhenAskedForDuration() throws Exception {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setQueryOrder(QueryOrder.BY_DURATION);
        condition.setLimit(5);

        dao.queryTraces(condition);

        final String sql = capturedSql.get(0);
        assertThat(sql).startsWith("select " + SPAN_TABLE + "." + OTLPSpanRecord.TRACE_ID + ", max(" + OTLPSpanRecord.DURATION + ")");
        assertThat(sql).doesNotContain("inner join");
        assertThat(sql).endsWith("order by max(" + OTLPSpanRecord.DURATION + ") desc limit 5");
        assertThat(capturedParams.get(0)).containsExactly(OTLPSpanRecord.INDEX_NAME);
    }

    @Test
    void queryTraceById_shouldFilterOnTheTraceIdColumn() throws Exception {
        dao.queryTraceById("0af7651916cd43dd8448eb211c80319c", null);

        final String sql = capturedSql.get(0);
        assertThat(sql).contains("from " + SPAN_TABLE);
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ? and " + OTLPSpanRecord.TRACE_ID + " = ?");
        assertThat(capturedParams.get(0)).containsExactly(OTLPSpanRecord.INDEX_NAME, "0af7651916cd43dd8448eb211c80319c");
    }
}
