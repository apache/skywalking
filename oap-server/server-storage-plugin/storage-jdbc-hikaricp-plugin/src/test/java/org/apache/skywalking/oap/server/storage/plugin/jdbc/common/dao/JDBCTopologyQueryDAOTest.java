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

import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
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
class JDBCTopologyQueryDAOTest {

    private static final String TABLE = "service_relation_server_side_20260406";
    private static final String INSTANCE_TABLE = "service_instance_relation_server_side_20260406";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCTopologyQueryDAO dao;
    private Duration duration;

    @BeforeEach
    void setUp() {
        dao = new JDBCTopologyQueryDAO(jdbcClient, tableHelper);

        duration = new Duration();
        duration.setStart("2026-04-06 0000");
        duration.setEnd("2026-04-06 2359");
        duration.setStep(Step.MINUTE);
    }

    @Test
    void loadServiceRelationsDetectedAtServerSide_withNoServiceIds_shouldNotAddServiceIdFilter() throws Exception {
        when(tableHelper.getTablesForRead(
            ServiceRelationServerSideMetrics.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        )).thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.loadServiceRelationsDetectedAtServerSide(duration);

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(sql).doesNotContain("and (");
        assertThat(sql).contains("group by");
    }

    @Test
    void loadServiceRelationsDetectedAtServerSide_withSingleServiceId_shouldAddOrCondition() throws Exception {
        when(tableHelper.getTablesForRead(
            ServiceRelationServerSideMetrics.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        )).thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.loadServiceRelationsDetectedAtServerSide(duration, Collections.singletonList("svc-1"));

        final String sql = capturedSql.get();
        assertThat(sql).contains("and (");
        assertThat(sql).contains(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID + "=?");
        assertThat(sql).contains(" or " + ServiceRelationServerSideMetrics.DEST_SERVICE_ID + "=?");
        // parentheses must be closed
        assertThat(sql).containsPattern("\\(.*=\\?.*or.*=\\?.*\\)");
    }

    @Test
    void loadServiceRelationsDetectedAtServerSide_withMultipleServiceIds_shouldChainOrConditions() throws Exception {
        when(tableHelper.getTablesForRead(
            ServiceRelationServerSideMetrics.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        )).thenReturn(Collections.singletonList(TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.loadServiceRelationsDetectedAtServerSide(duration, Arrays.asList("svc-1", "svc-2"));

        final String sql = capturedSql.get();
        assertThat(sql).contains("and (");
        // two pairs of source/dest conditions connected with OR
        assertThat(countOccurrences(sql, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID + "=?")).isEqualTo(2);
        assertThat(countOccurrences(sql, ServiceRelationServerSideMetrics.DEST_SERVICE_ID + "=?")).isEqualTo(2);
        // parentheses must be closed
        assertThat(sql).containsPattern("and \\(.*\\)");
    }

    @Test
    void loadInstanceRelationDetectedAtServerSide_shouldUseBidirectionalCondition() throws Exception {
        when(tableHelper.getTablesForRead(
            ServiceInstanceRelationServerSideMetrics.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        )).thenReturn(Collections.singletonList(INSTANCE_TABLE));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            return Collections.emptyList();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.loadInstanceRelationDetectedAtServerSide("client-svc", "server-svc", duration);

        final String sql = capturedSql.get();
        assertThat(sql).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        // bidirectional: (source=A and dest=B) OR (source=B and dest=A)
        assertThat(sql).contains("((");
        assertThat(sql).contains(") or (");
        assertThat(sql).contains("))");
        assertThat(countOccurrences(sql, ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID + "=?")).isEqualTo(2);
        assertThat(countOccurrences(sql, ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID + "=?")).isEqualTo(2);
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
