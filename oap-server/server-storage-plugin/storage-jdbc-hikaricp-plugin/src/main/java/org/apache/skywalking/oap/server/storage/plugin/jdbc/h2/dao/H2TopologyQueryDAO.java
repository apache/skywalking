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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.DownSamplingModelNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

/**
 * @author wusheng
 */
public class H2TopologyQueryDAO implements ITopologyQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TopologyQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public List<Call.CallDetail> loadSpecifiedServerSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideMetrics.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, true);
    }

    @Override public List<Call.CallDetail> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideMetrics.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, false);
    }

    @Override public List<Call.CallDetail> loadServerSideServiceRelations(Step step, long startTB,
        long endTB) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideMetrics.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), false);
    }

    @Override public List<Call.CallDetail> loadClientSideServiceRelations(Step step, long startTB,
        long endTB) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideMetrics.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), true);
    }

    @Override
    public List<Call.CallDetail> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, EndpointRelationServerSideMetrics.INDEX_NAME);

        List<Call.CallDetail> calls = loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideMetrics.SOURCE_ENDPOINT_ID, EndpointRelationServerSideMetrics.DEST_ENDPOINT_ID, destEndpointId, false);
        calls.addAll(loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideMetrics.SOURCE_ENDPOINT_ID, EndpointRelationServerSideMetrics.DEST_ENDPOINT_ID, destEndpointId, true));
        return calls;
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName, long startTB, long endTB, String sourceCName,
        String destCName, List<Integer> serviceIds, boolean isClientSide) throws IOException {
        Object[] conditions = new Object[serviceIds.size() * 2 + 2];
        conditions[0] = startTB;
        conditions[1] = endTB;
        StringBuilder serviceIdMatchSql = new StringBuilder();
        if (serviceIds.size() > 0) {
            serviceIdMatchSql.append("and (");
            for (int i = 0; i < serviceIds.size(); i++) {
                serviceIdMatchSql.append(sourceCName + "=? or " + destCName + "=? ");
                conditions[i * 2 + 2] = serviceIds.get(i);
                conditions[i * 2 + 1 + 2] = serviceIds.get(i);
                if (i != serviceIds.size() - 1) {
                    serviceIdMatchSql.append("or ");
                }
            }
            serviceIdMatchSql.append(")");
        }
        List<Call.CallDetail> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select "
                    + Metrics.ENTITY_ID
                    + " from " + tableName + " where "
                    + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? "
                    + serviceIdMatchSql.toString()
                    + " group by " + Metrics.ENTITY_ID,
                conditions)) {
                buildCalls(resultSet, calls, isClientSide);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpointFromSide(String tableName, long startTB, long endTB, String sourceCName,
        String destCName, int id, boolean isSourceId) throws IOException {
        Object[] conditions = new Object[3];
        conditions[0] = startTB;
        conditions[1] = endTB;
        conditions[2] = id;
        List<Call.CallDetail> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select "
                    + Metrics.ENTITY_ID
                    + " from " + tableName + " where "
                    + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? and "
                    + (isSourceId ? sourceCName : destCName) + "=?"
                    + " group by " + Metrics.ENTITY_ID,
                conditions)) {
                buildCalls(resultSet, calls, isSourceId);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private void buildCalls(ResultSet resultSet, List<Call.CallDetail> calls,
        boolean isClientSide) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            ServiceRelationDefineUtil.RelationDefine relationDefine = ServiceRelationDefineUtil.splitEntityId(entityId);

            call.setSource(relationDefine.getSource());
            call.setTarget(relationDefine.getDest());
            call.setComponentId(relationDefine.getComponentId());
            if (isClientSide) {
                call.setDetectPoint(DetectPoint.CLIENT);
            } else {
                call.setDetectPoint(DetectPoint.SERVER);
            }
            call.setId(entityId);
            calls.add(call);
        }
    }
}
