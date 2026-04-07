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

import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
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

import java.util.ArrayList;
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
class JDBCMetadataQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCMetadataQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCMetadataQueryDAO(jdbcClient, 100, tableHelper);
    }

    @Test
    void findEndpoint_shouldContainTableColumnConditionOnlyOnce() throws Exception {
        when(tableHelper.getTablesWithinTTL(EndpointTraffic.INDEX_NAME))
            .thenReturn(Collections.singletonList("endpoint_traffic"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        final AtomicReference<Object[]> capturedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.set(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.findEndpoint("keyword", "serviceId", 10, null);

        final String sql = capturedSql.get();
        final long tableColumnCount = countOccurrences(sql, JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(tableColumnCount)
            .as("TABLE_COLUMN condition should appear exactly once in WHERE clause")
            .isEqualTo(1);

        final Object[] params = capturedParams.get();
        final long tableNameParamCount = countOccurrences(params, EndpointTraffic.INDEX_NAME);
        assertThat(tableNameParamCount)
            .as("EndpointTraffic.INDEX_NAME should be bound exactly once as a parameter")
            .isEqualTo(1);
    }

    @Test
    void findEndpoint_shouldFilterByServiceId() throws Exception {
        when(tableHelper.getTablesWithinTTL(EndpointTraffic.INDEX_NAME))
            .thenReturn(Collections.singletonList("endpoint_traffic"));

        final AtomicReference<String> capturedSql = new AtomicReference<>();
        final AtomicReference<Object[]> capturedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedSql.set(invocation.getArgument(0));
            final Object[] allArgs = invocation.getArguments();
            capturedParams.set(Arrays.copyOfRange(allArgs, 2, allArgs.length));
            return new ArrayList<>();
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        dao.findEndpoint(null, "my-service", 10, null);

        assertThat(capturedSql.get()).contains(EndpointTraffic.SERVICE_ID + "=?");
        assertThat(capturedParams.get()).contains("my-service");
    }

    private long countOccurrences(final String text, final String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    private long countOccurrences(final Object[] array, final Object target) {
        long count = 0;
        for (final Object item : array) {
            if (target.equals(item)) {
                count++;
            }
        }
        return count;
    }
}
