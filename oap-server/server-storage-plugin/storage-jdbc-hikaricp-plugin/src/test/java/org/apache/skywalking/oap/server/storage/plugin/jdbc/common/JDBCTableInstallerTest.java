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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JDBCTableInstallerTest {
    private static final String TABLE = "jdbc_installer_test";

    private static MockedStatic<DefaultScopeDefine> DEFAULT_SCOPE_DEFINE_MOCKED_STATIC;

    @Mock
    private JDBCClient client;

    @Mock
    private ModuleManager moduleManager;

    @BeforeAll
    static void setup() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC = mockStatic(DefaultScopeDefine.class);
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.when(() -> DefaultScopeDefine.nameOf(-1)).thenReturn("any");
    }

    @AfterAll
    static void tearDown() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.close();
    }

    /**
     * The column added before a later statement failed stays added, so the table's outcome names it.
     */
    @Test
    void recordsTheColumnsAddedBeforeALaterStatementFails() throws Exception {
        when(client.tableExists(TABLE)).thenReturn(true);
        when(client.getTableColumns(TABLE)).thenReturn(Set.of("id", "table_name", "a"));
        when(client.indexExists(eq(TABLE), anyString())).thenReturn(false);
        doAnswer(i -> {
            if (i.<String>getArgument(0).startsWith("CREATE INDEX")) {
                throw new SQLException("index creation failed");
            }
            return null;
        }).when(client).execute(anyString());

        final StorageManipulationOpt opt = StorageManipulationOpt.bootInstall();
        assertThrows(SQLException.class,
            () -> new JDBCTableInstaller(client, moduleManager).createTable(model(), opt));

        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        assertEquals(1, outcomes.size(), outcomes::toString);
        assertEquals("table", outcomes.get(0).getResourceType());
        assertEquals(TABLE, outcomes.get(0).getResourceName());
        assertEquals(StorageManipulationOpt.Outcome.UPDATED, outcomes.get(0).getStatus());
        assertEquals("adds columns [b]; a later statement failed", outcomes.get(0).getDiff());
    }

    /**
     * A table that has every column but lacks an index gets the index, and its outcome says so.
     */
    @Test
    void recordsTheIndexesAddedToATableWithEveryColumn() throws Exception {
        when(client.tableExists(TABLE)).thenReturn(true);
        when(client.getTableColumns(TABLE)).thenReturn(Set.of("id", "table_name", "a", "b"));
        final String tableNameIndex = "idx_" + Math.abs((TABLE + "_" + JDBCTableInstaller.TABLE_COLUMN).hashCode());
        when(client.indexExists(eq(TABLE), anyString())).thenAnswer(i -> !tableNameIndex.equals(i.getArgument(1)));

        final StorageManipulationOpt opt = StorageManipulationOpt.bootInstall();
        new JDBCTableInstaller(client, moduleManager).createTable(model(), opt);

        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        assertEquals(1, outcomes.size(), outcomes::toString);
        assertEquals(StorageManipulationOpt.Outcome.UPDATED, outcomes.get(0).getStatus());
        assertEquals("adds indexes [" + tableNameIndex + "]", outcomes.get(0).getDiff());
    }

    @Test
    void recordsAMatchWhenNothingIsMissing() throws Exception {
        when(client.tableExists(TABLE)).thenReturn(true);
        when(client.getTableColumns(TABLE)).thenReturn(Set.of("id", "table_name", "a", "b"));
        when(client.indexExists(eq(TABLE), anyString())).thenReturn(true);

        final StorageManipulationOpt opt = StorageManipulationOpt.bootInstall();
        new JDBCTableInstaller(client, moduleManager).createTable(model(), opt);

        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        assertEquals(1, outcomes.size(), outcomes::toString);
        assertEquals(StorageManipulationOpt.Outcome.MATCHED, outcomes.get(0).getStatus());
        assertTrue(outcomes.get(0).getDiff().contains("nothing to add"), outcomes::toString);
    }

    private static Model model() throws Exception {
        return new StorageModels().add(TestTableModel.class, -1,
            new Storage(TABLE, false, DownSampling.None), StorageManipulationOpt.bootInstall());
    }

    @Stream(name = TABLE, scopeId = -1, builder = TestTableModel.Builder.class, processor = ManagementStreamProcessor.class)
    private static class TestTableModel {
        @Column(name = "a", storageOnly = true)
        private String a;

        @Column(name = "b", storageOnly = true)
        private String b;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }
}
