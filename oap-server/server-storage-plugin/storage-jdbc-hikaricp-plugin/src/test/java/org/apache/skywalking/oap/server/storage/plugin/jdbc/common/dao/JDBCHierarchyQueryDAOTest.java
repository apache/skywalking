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

import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
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
class JDBCHierarchyQueryDAOTest {

    private static final String TABLE = "hierarchy_table";

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCHierarchyQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCHierarchyQueryDAO(jdbcClient, 100, tableHelper);
    }

    @Test
    void buildSQLForReadAllServiceRelations_shouldContainTableColumnAndLimit() {
        final SQLAndParameters result = dao.buildSQLForReadAllServiceRelations(TABLE);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.parameters()).contains(ServiceHierarchyRelationTraffic.INDEX_NAME);
        assertThat(result.sql()).contains("limit 200");
    }

    @Test
    void buildSQLForReadInstanceRelations_shouldContainBidirectionalCondition() {
        final SQLAndParameters result = dao.buildSQLForReadInstanceRelations(TABLE, "instance-1", 1);

        assertThat(result.sql()).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(result.sql()).contains(InstanceHierarchyRelationTraffic.INSTANCE_ID + "=?");
        assertThat(result.sql()).contains(InstanceHierarchyRelationTraffic.SERVICE_LAYER + "=?");
        assertThat(result.sql()).contains(InstanceHierarchyRelationTraffic.RELATED_INSTANCE_ID + "=?");
        assertThat(result.sql()).contains(InstanceHierarchyRelationTraffic.RELATED_SERVICE_LAYER + "=?");
    }

    @Test
    void buildSQLForReadInstanceRelations_shouldHaveOrBetweenDirections() {
        final SQLAndParameters result = dao.buildSQLForReadInstanceRelations(TABLE, "instance-1", 1);

        assertThat(result.sql()).contains(") or (");
    }

    @Test
    void buildSQLForReadInstanceRelations_shouldBindParametersCorrectly() {
        final SQLAndParameters result = dao.buildSQLForReadInstanceRelations(TABLE, "inst-abc", 3);

        assertThat(result.parameters())
            .containsExactly(
                InstanceHierarchyRelationTraffic.INDEX_NAME,
                "inst-abc", 3,
                "inst-abc", 3
            );
    }
}
