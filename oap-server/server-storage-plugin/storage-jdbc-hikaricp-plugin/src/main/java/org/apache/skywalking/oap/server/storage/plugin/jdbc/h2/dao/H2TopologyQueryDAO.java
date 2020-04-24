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
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2TopologyQueryDAO implements ITopologyQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TopologyQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                          long endTB,
                                                                          List<String> serviceIds) throws IOException {
        return loadServiceCalls(
            ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB,
                                                                         List<String> serviceIds) throws IOException {
        return loadServiceCalls(
            ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                          long endTB) throws IOException {
        return loadServiceCalls(
            ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB) throws IOException {
        return loadServiceCalls(
            ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        return loadServiceInstanceCalls(
            ServiceInstanceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
            DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        return loadServiceInstanceCalls(
            ServiceInstanceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
            ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
            DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB,
                                                      long endTB,
                                                      String destEndpointId) throws IOException {
        List<Call.CallDetail> calls = loadEndpointFromSide(
            EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, false
        );
        calls.addAll(
            loadEndpointFromSide(EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                                 EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                                 EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, true
            ));
        return calls;
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName,
                                                   long startTB,
                                                   long endTB,
                                                   String sourceCName,
                                                   String destCName,
                                                   List<String> serviceIds,
                                                   DetectPoint detectPoint) throws IOException {
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
                "select " + Metrics.ENTITY_ID + ", " + ServiceRelationServerSideMetrics.COMPONENT_ID
                    + " from " + tableName + " where " + Metrics.TIME_BUCKET + ">= ? and "
                    + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql
                    .toString() +
                    " group by " + Metrics.ENTITY_ID + "," + ServiceRelationServerSideMetrics.COMPONENT_ID, conditions
            )) {
                buildServiceCalls(resultSet, calls, detectPoint);
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
                                                           String sourceServiceId,
                                                           String destServiceId,
                                                           DetectPoint detectPoint) throws IOException {
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
                "select " + Metrics.ENTITY_ID + ", " + ServiceInstanceRelationServerSideMetrics.COMPONENT_ID
                    + " from " + tableName + " where " + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql
                    .toString() + " group by " + Metrics.ENTITY_ID + ", " + ServiceInstanceRelationServerSideMetrics.COMPONENT_ID,
                conditions
            )) {
                buildInstanceCalls(resultSet, calls, detectPoint);
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
                "select " + Metrics.ENTITY_ID + " from " + tableName
                    + " where " + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? and "
                    + (isSourceId ? sourceCName : destCName) + "=?"
                    + " group by " + Metrics.ENTITY_ID,
                conditions
            )) {
                buildEndpointCalls(resultSet, calls, DetectPoint.SERVER);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }

    private void buildServiceCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                   DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            final int componentId = resultSet.getInt(ServiceRelationServerSideMetrics.COMPONENT_ID);
            call.buildFromServiceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
    }

    private void buildInstanceCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                    DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            final int componentId = resultSet.getInt(ServiceRelationServerSideMetrics.COMPONENT_ID);
            call.buildFromInstanceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
    }

    private void buildEndpointCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                    DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            call.buildFromEndpointRelation(entityId, detectPoint);
            calls.add(call);
        }
    }
}
