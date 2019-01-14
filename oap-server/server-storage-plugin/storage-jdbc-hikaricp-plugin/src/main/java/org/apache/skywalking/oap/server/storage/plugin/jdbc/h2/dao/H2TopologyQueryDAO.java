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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationClientSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
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

    @Override public List<Call> loadSpecifiedServerSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds, true);
    }

    @Override public List<Call> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds, false);
    }

    @Override public List<Call> loadServerSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, new ArrayList<>(0), false);
    }

    @Override public List<Call> loadClientSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, new ArrayList<>(0), true);
    }

    @Override public List<Call> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, EndpointRelationServerSideIndicator.INDEX_NAME);

        return loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideIndicator.SOURCE_ENDPOINT_ID, EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, destEndpointId, false);
    }

    private List<Call> loadServiceCalls(String tableName, long startTB, long endTB, String sourceCName,
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
        List<Call> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select "
                    + Indicator.ENTITY_ID
                    + " from " + tableName + " where "
                    + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? "
                    + serviceIdMatchSql.toString()
                    + " group by " + Indicator.ENTITY_ID,
                conditions)) {
                buildCalls(resultSet, calls, isClientSide);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private List<Call> loadEndpointFromSide(String tableName, long startTB, long endTB, String sourceCName,
        String destCName, int id, boolean isSourceId) throws IOException {
        Object[] conditions = new Object[3];
        conditions[0] = startTB;
        conditions[1] = endTB;
        conditions[2] = id;
        List<Call> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select "
                    + Indicator.ENTITY_ID
                    + " from " + tableName + " where "
                    + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? and "
                    + (isSourceId ? sourceCName : destCName) + "=?"
                    + " group by " + Indicator.ENTITY_ID,
                conditions)) {
                buildCalls(resultSet, calls, isSourceId);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private void buildCalls(ResultSet resultSet, List<Call> calls, boolean isClientSide) throws SQLException {
        while (resultSet.next()) {
            Call call = new Call();
            String entityId = resultSet.getString(Indicator.ENTITY_ID);
            Integer[] entityIds = ServiceRelation.splitEntityId(entityId);

            call.setSource(entityIds[0]);
            call.setTarget(entityIds[1]);
            call.setComponentId(entityIds[2]);
            call.setDetectPoint(isClientSide ? DetectPoint.CLIENT : DetectPoint.SERVER);
            call.setId(entityId);
            calls.add(call);
        }
    }
}
