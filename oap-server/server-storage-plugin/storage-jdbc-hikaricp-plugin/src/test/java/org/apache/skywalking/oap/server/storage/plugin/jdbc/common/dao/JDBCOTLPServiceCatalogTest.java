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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The service catalog read narrows by layer in the query itself.
 */
public class JDBCOTLPServiceCatalogTest {
    private static final String TABLE = OTLPServiceTraffic.INDEX_NAME + "_20261023";

    private JDBCOTLPTraceQueryDAO dao;
    private final List<String> sql = new ArrayList<>();
    private final List<Object[]> params = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        final JDBCClient jdbcClient = mock(JDBCClient.class);
        final TableHelper tableHelper = mock(TableHelper.class);
        when(tableHelper.getTablesWithinTTL(OTLPServiceTraffic.INDEX_NAME)).thenReturn(List.of(TABLE));
        doAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            final Object[] args = invocation.getArguments();
            params.add(Arrays.copyOfRange(args, 2, args.length));
            return null;
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));
        dao = new JDBCOTLPTraceQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    public void withoutALayerEveryRowIsRead() {
        dao.getServices(null);

        assertFalse(sql.get(0).contains("otlp_service_layer = ?"), sql.get(0));
        assertArrayEquals(new Object[] {OTLPServiceTraffic.INDEX_NAME}, params.get(0));
    }

    @Test
    public void aLayerReadsOnlyItsOwnRows() {
        dao.getServices("OTLP_GENERAL");

        assertTrue(sql.get(0).contains(" and otlp_service_layer = ? limit "), sql.get(0));
        assertArrayEquals(new Object[] {OTLPServiceTraffic.INDEX_NAME, "OTLP_GENERAL"}, params.get(0));
    }

    @Test
    public void aServiceInEveryDaysTableIsOneRow() throws Exception {
        final JDBCClient jdbcClient = mock(JDBCClient.class);
        final TableHelper tableHelper = mock(TableHelper.class);
        when(tableHelper.getTablesWithinTTL(OTLPServiceTraffic.INDEX_NAME))
            .thenReturn(List.of(TABLE, OTLPServiceTraffic.INDEX_NAME + "_20261024"));
        doAnswer(invocation -> {
            final ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true, false);
            when(rows.getObject(OTLPServiceTraffic.SERVICE_NAME)).thenReturn("cart");
            when(rows.getObject(OTLPServiceTraffic.LAYER)).thenReturn("OTLP_GENERAL");
            when(rows.getObject(OTLPServiceTraffic.TIME_BUCKET)).thenReturn(202610231200L);
            return ((JDBCClient.ResultHandler<?>) invocation.getArgument(1)).handle(rows);
        }).when(jdbcClient).executeQuery(anyString(), any(), any(Object[].class));

        final List<OTLPServiceTraffic> services = new JDBCOTLPTraceQueryDAO(jdbcClient, tableHelper).getServices(null);

        assertEquals(1, services.size(), services.toString());
        assertEquals("cart", services.get(0).getServiceName());
    }
}
