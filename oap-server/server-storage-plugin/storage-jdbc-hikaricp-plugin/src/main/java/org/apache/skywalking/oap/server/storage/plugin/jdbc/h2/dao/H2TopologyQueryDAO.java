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
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.manual.RelationDefineUtil;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2TopologyQueryDAO implements ITopologyQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TopologyQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<Call.CallDetail> loadSpecifiedServerSideServiceRelations(Downsampling downsampling,
                                                                         long startTB,
                                                                         long endTB,
                                                                         List<Integer> serviceIds) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceRelationServerSideMetrics.INDEX_NAME);
        return loadServiceCalls(
            tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, false
        );
    }

    @Override
    public List<Call.CallDetail> loadSpecifiedClientSideServiceRelations(Downsampling downsampling,
                                                                         long startTB,
                                                                         long endTB,
                                                                         List<Integer> serviceIds) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceRelationClientSideMetrics.INDEX_NAME);
        return loadServiceCalls(
            tableName, startTB, endTB, ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, serviceIds, true
        );
    }

    @Override
    public List<Call.CallDetail> loadServerSideServiceRelations(Downsampling downsampling, long startTB,
                                                                long endTB) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceRelationServerSideMetrics.INDEX_NAME);
        return loadServiceCalls(
            tableName, startTB, endTB, ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), false
        );
    }

    @Override
    public List<Call.CallDetail> loadClientSideServiceRelations(Downsampling downsampling, long startTB,
                                                                long endTB) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceRelationClientSideMetrics.INDEX_NAME);
        return loadServiceCalls(
            tableName, startTB, endTB, ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), true
        );
    }

    @Override
    public List<Call.CallDetail> loadServerSideServiceInstanceRelations(int clientServiceId,
                                                                        int serverServiceId,
                                                                        Downsampling downsampling,
                                                                        long startTB,
                                                                        long endTB) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceInstanceRelationServerSideMetrics.INDEX_NAME);
        return loadServiceInstanceCalls(
            tableName, startTB, endTB, ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId, false
        );
    }

    @Override
    public List<Call.CallDetail> loadClientSideServiceInstanceRelations(int clientServiceId,
                                                                        int serverServiceId,
                                                                        Downsampling downsampling,
                                                                        long startTB,
                                                                        long endTB) throws IOException {
        String tableName = ModelName.build(downsampling, ServiceInstanceRelationClientSideMetrics.INDEX_NAME);
        return loadServiceInstanceCalls(
            tableName, startTB, endTB, ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId, true
        );
    }

    @Override
    public List<Call.CallDetail> loadSpecifiedDestOfServerSideEndpointRelations(Downsampling downsampling,
                                                                                long startTB,
                                                                                long endTB,
                                                                                String destEndpointId) throws IOException {
        String tableName = ModelName.build(downsampling, EndpointRelationServerSideMetrics.INDEX_NAME);

        List<Call.CallDetail> calls = loadEndpointFromSide(
            tableName, startTB, endTB, EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, false
        );
        calls.addAll(loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                                          EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, true
        ));
        return calls;
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName,
                                                   long startTB,
                                                   long endTB,
                                                   String sourceCName,
                                                   String destCName,
                                                   List<Integer> serviceIds,
                                                   boolean isClientSide) throws IOException {
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
            try (ResultSet resultSet = h2Client.executeQuery(
                connection,
                "select " + Metrics.ENTITY_ID + " from " + tableName + " where " + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql
                    .toString() + " group by " + Metrics.ENTITY_ID, conditions
            )) {
                buildCalls(resultSet, calls, isClientSide);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private List<Call.CallDetail> loadServiceInstanceCalls(String tableName,
                                                           long startTB,
                                                           long endTB,
                                                           String sourceCName,
                                                           String descCName,
                                                           int sourceServiceId,
                                                           int destServiceId,
                                                           boolean isClientSide) throws IOException {
        Object[] conditions = new Object[] {
            startTB,
            endTB,
            sourceServiceId,
            destServiceId,
            destServiceId,
            sourceServiceId
        };
        StringBuilder serviceIdMatchSql = new StringBuilder("and ((").append(sourceCName)
                                                                     .append("=? and ")
                                                                     .append(descCName)
                                                                     .append("=?")
                                                                     .append(") or (")
                                                                     .append(sourceCName)
                                                                     .append("=? and ")
                                                                     .append(descCName)
                                                                     .append("=?")
                                                                     .append("))");
        List<Call.CallDetail> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection,
                "select " + Metrics.ENTITY_ID + " from " + tableName + " where " + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql
                    .toString() + " group by " + Metrics.ENTITY_ID, conditions
            )) {
                buildCalls(resultSet, calls, isClientSide);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpointFromSide(String tableName,
                                                       long startTB,
                                                       long endTB,
                                                       String sourceCName,
                                                       String destCName,
                                                       String id,
                                                       boolean isSourceId) throws IOException {
        Object[] conditions = new Object[3];
        conditions[0] = startTB;
        conditions[1] = endTB;
        conditions[2] = id;
        List<Call.CallDetail> calls = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection,
                "select " + Metrics.ENTITY_ID + " from " + tableName + " where " + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? and " + (isSourceId ? sourceCName : destCName) + "=?" + " group by " + Metrics.ENTITY_ID,
                conditions
            )) {
                buildEndpointCalls(resultSet, calls, isSourceId);
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
            RelationDefineUtil.RelationDefine relationDefine = RelationDefineUtil.splitEntityId(entityId);

            call.setSource(String.valueOf(relationDefine.getSource()));
            call.setTarget(String.valueOf(relationDefine.getDest()));
            call.setComponentId(relationDefine.getComponentId());
            if (isClientSide) {
                call.setDetectPoint(DetectPoint.CLIENT);
            } else {
                call.setDetectPoint(DetectPoint.SERVER);
            }
            call.generateID();
            calls.add(call);
        }
    }

    private void buildEndpointCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                    boolean isClientSide) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            RelationDefineUtil.EndpointRelationDefine relationDefine = RelationDefineUtil.splitEndpointRelationEntityId(
                entityId);

            if (isClientSide) {
                call.setDetectPoint(DetectPoint.CLIENT);
            } else {
                call.setDetectPoint(DetectPoint.SERVER);
            }
            call.setSource(EndpointTraffic.buildId(relationDefine.getSourceServiceId(), relationDefine.getSource(),
                                                   call.getDetectPoint()
            ));
            call.setTarget(EndpointTraffic.buildId(relationDefine.getDestServiceId(), relationDefine.getDest(),
                                                   call.getDetectPoint()
            ));
            call.setComponentId(relationDefine.getComponentId());

            call.generateID();
            calls.add(call);
        }
    }
}
