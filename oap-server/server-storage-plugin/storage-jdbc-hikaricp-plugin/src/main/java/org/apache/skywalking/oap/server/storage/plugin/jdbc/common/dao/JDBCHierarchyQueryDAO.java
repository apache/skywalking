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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCEntityConverters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import static java.util.stream.Collectors.toList;

public class JDBCHierarchyQueryDAO implements IHierarchyQueryDAO {
    private final JDBCClient jdbcClient;
    private final int queryMaxSize;
    private final TableHelper tableHelper;

    public JDBCHierarchyQueryDAO(JDBCClient jdbcClient, int metadataQueryMaxSize, TableHelper tableHelper) {
        this.jdbcClient = jdbcClient;
        this.queryMaxSize = metadataQueryMaxSize * 2;
        this.tableHelper = tableHelper;
    }

    @Override
    public List<ServiceHierarchyRelationTraffic> readAllServiceHierarchyRelations() throws Exception {
        final var results = new ArrayList<ServiceHierarchyRelationTraffic>();
        final var tables = tableHelper.getTablesWithinTTL(ServiceHierarchyRelationTraffic.INDEX_NAME);

        for (final var table : tables) {
            final var sqlAndParameters = buildSQLForReadAllServiceRelations(table);
            results.addAll(jdbcClient.executeQuery(
                               sqlAndParameters.sql(),
                               this::buildServiceRelations,
                               sqlAndParameters.parameters()
                           )
            );
        }
        return results
            .stream()
            .limit(queryMaxSize)
            .collect(toList());
    }

    @Override
    public List<InstanceHierarchyRelationTraffic> readInstanceHierarchyRelations(final String instanceId,
                                                                                 final String layer) throws Exception {
        final var results = new ArrayList<InstanceHierarchyRelationTraffic>();
        final var tables = tableHelper.getTablesWithinTTL(InstanceHierarchyRelationTraffic.INDEX_NAME);
        final var layerValue = Layer.valueOf(layer).value();
        List<Call.CallDetail> calls = new ArrayList<>();

        for (final var table : tables) {
            final var sqlAndParameters = buildSQLForReadInstanceRelations(table, instanceId, layerValue);
            results.addAll(jdbcClient.executeQuery(
                               sqlAndParameters.sql(),
                               this::buildInstanceRelations,
                               sqlAndParameters.parameters()
                           )
            );
        }
        return results;
    }

    protected SQLAndParameters buildSQLForReadAllServiceRelations(String table) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>(5);
        sql.append("select * from ").append(table)
           .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(ServiceHierarchyRelationTraffic.INDEX_NAME);
        sql.append(" limit ").append(queryMaxSize);
        return new SQLAndParameters(sql.toString(), parameters);
    }

    protected SQLAndParameters buildSQLForReadInstanceRelations(String table, String instanceId, int layerValue) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>(5) {
            {
                add(InstanceHierarchyRelationTraffic.INDEX_NAME);
                add(instanceId);
                add(layerValue);
                add(instanceId);
                add(layerValue);
            }
        };
        sql.append("select * from ").append(table)
           .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ")
           .append("and ((").append(InstanceHierarchyRelationTraffic.INSTANCE_ID)
           .append("=? and ")
           .append(InstanceHierarchyRelationTraffic.SERVICE_LAYER)
           .append("=?")
           .append(") or (")
           .append(InstanceHierarchyRelationTraffic.RELATED_INSTANCE_ID)
           .append("=? and ")
           .append(InstanceHierarchyRelationTraffic.RELATED_SERVICE_LAYER)
           .append("=?")
           .append("))");
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private List<ServiceHierarchyRelationTraffic> buildServiceRelations(ResultSet resultSet) throws SQLException {
        final var relations = new ArrayList<ServiceHierarchyRelationTraffic>();
        while (resultSet.next()) {
            relations.add(new ServiceHierarchyRelationTraffic.Builder().storage2Entity(
                JDBCEntityConverters.toEntity(resultSet)));
        }
        return relations;
    }

    private List<InstanceHierarchyRelationTraffic> buildInstanceRelations(ResultSet resultSet) throws SQLException {
        final var relations = new ArrayList<InstanceHierarchyRelationTraffic>();
        while (resultSet.next()) {
            relations.add(new InstanceHierarchyRelationTraffic.Builder().storage2Entity(
                JDBCEntityConverters.toEntity(resultSet)));
        }
        return relations;
    }
}
