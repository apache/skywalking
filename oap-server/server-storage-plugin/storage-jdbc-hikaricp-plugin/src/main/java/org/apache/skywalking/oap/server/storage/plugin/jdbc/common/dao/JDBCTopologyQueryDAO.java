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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCTopologyQueryDAO implements ITopologyQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration,
                                                                          List<String> serviceIds) throws IOException {
        return loadServiceCalls(
            ServiceRelationServerSideMetrics.INDEX_NAME, duration,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                         List<String> serviceIds) throws IOException {
        return loadServiceCalls(
            ServiceRelationClientSideMetrics.INDEX_NAME, duration,
            ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException {
        return loadServiceCalls(
            ServiceRelationServerSideMetrics.INDEX_NAME, duration,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException {
        return loadServiceCalls(
            ServiceRelationClientSideMetrics.INDEX_NAME, duration,
            ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationClientSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return loadServiceInstanceCalls(
            ServiceInstanceRelationServerSideMetrics.INDEX_NAME, duration,
            ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
            DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return loadServiceInstanceCalls(
            ServiceInstanceRelationClientSideMetrics.INDEX_NAME, duration,
            ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
            DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(Duration duration,
                                                      String destEndpointId) throws IOException {
        List<Call.CallDetail> calls = loadEndpointFromSide(
            EndpointRelationServerSideMetrics.INDEX_NAME, duration,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, false
        );
        calls.addAll(
            loadEndpointFromSide(EndpointRelationServerSideMetrics.INDEX_NAME, duration,
                                 EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                                 EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, true
            ));
        return calls;
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId, Duration duration) throws IOException {
        return loadProcessFromSide(duration, serviceInstanceId, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId, Duration duration) throws IOException {
        return loadProcessFromSide(duration, serviceInstanceId, DetectPoint.SERVER);
    }

    @SneakyThrows
    private List<Call.CallDetail> loadServiceCalls(String tableName,
                                                   Duration duration,
                                                   String sourceCName,
                                                   String destCName,
                                                   List<String> serviceIds,
                                                   DetectPoint detectPoint) {
        final var tables = tableHelper.getTablesForRead(
            tableName,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var calls = new ArrayList<Call.CallDetail>();

        for (String table : tables) {
            Object[] conditions = new Object[serviceIds.size() * 2 + 3];
            conditions[0] = tableName;
            conditions[1] = duration.getStartTimeBucket();
            conditions[2] = duration.getEndTimeBucket();
            StringBuilder serviceIdMatchSql = new StringBuilder();
            if (serviceIds.size() > 0) {
                serviceIdMatchSql.append("and (");
                for (int i = 0; i < serviceIds.size(); i++) {
                    serviceIdMatchSql.append(sourceCName + "=? or " + destCName + "=? ");
                    conditions[i * 2 + 3] = serviceIds.get(i);
                    conditions[i * 2 + 1 + 3] = serviceIds.get(i);
                    if (i != serviceIds.size() - 1) {
                        serviceIdMatchSql.append("or ");
                    }
                }
                serviceIdMatchSql.append(")");
            }
            jdbcClient.executeQuery(
                "select " + Metrics.ENTITY_ID + ", " + ServiceRelationServerSideMetrics.COMPONENT_IDS
                    + " from " + table + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and "
                    + Metrics.TIME_BUCKET + ">= ? and "
                    + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql +
                    " group by " + Metrics.ENTITY_ID + "," + ServiceRelationServerSideMetrics.COMPONENT_IDS,
                resultSet -> {
                    buildServiceCalls(resultSet, calls, detectPoint);
                    return null;
                },
                conditions
            );
        }

        return calls;
    }

    @SneakyThrows
    private List<Call.CallDetail> loadServiceInstanceCalls(String tableName,
                                                           Duration duration,
                                                           String sourceCName,
                                                           String descCName,
                                                           String sourceServiceId,
                                                           String destServiceId,
                                                           DetectPoint detectPoint) throws IOException {
        final var tables = tableHelper.getTablesForRead(
            tableName,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        List<Call.CallDetail> calls = new ArrayList<>();

        for (String table : tables) {
            Object[] conditions = new Object[]{
                tableName,
                duration.getStartTimeBucket(),
                duration.getEndTimeBucket(),
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
            jdbcClient.executeQuery(
                "select " + Metrics.ENTITY_ID
                    + " from " + table + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and "
                    + Metrics.TIME_BUCKET + ">= ? and "
                    + Metrics.TIME_BUCKET + "<=? " + serviceIdMatchSql + " group by " + Metrics.ENTITY_ID,
                resultSet -> {
                    buildInstanceCalls(resultSet, calls, detectPoint);
                    return null;
                },
                conditions
            );
        }
        return calls;
    }

    @SneakyThrows
    private List<Call.CallDetail> loadEndpointFromSide(String tableName,
                                                       Duration duration,
                                                       String sourceCName,
                                                       String destCName,
                                                       String id,
                                                       boolean isSourceId) throws IOException {
        List<Call.CallDetail> calls = new ArrayList<>();

        final var tables = tableHelper.getTablesForRead(
            tableName,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        for (String table : tables) {
            Object[] conditions = new Object[4];
            conditions[0] = tableName;
            conditions[1] = duration.getStartTimeBucket();
            conditions[2] = duration.getEndTimeBucket();
            conditions[3] = id;
            jdbcClient.executeQuery(
                "select " + Metrics.ENTITY_ID + " from " + table
                    + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and "
                    + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? and "
                    + (isSourceId ? sourceCName : destCName) + "=?"
                    + " group by " + Metrics.ENTITY_ID,
                resultSet -> {
                    buildEndpointCalls(resultSet, calls, DetectPoint.SERVER);
                    return null;
                },
                conditions
            );
        }
        return calls;
    }

    @SneakyThrows
    private List<Call.CallDetail> loadProcessFromSide(Duration duration,
                                                       String instanceId,
                                                       DetectPoint detectPoint) throws IOException {
        final var tableName = (detectPoint == DetectPoint.SERVER ? ProcessRelationServerSideMetrics.INDEX_NAME : ProcessRelationClientSideMetrics.INDEX_NAME);
        final var tables = tableHelper.getTablesForRead(
            tableName,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );

        List<Call.CallDetail> calls = new ArrayList<>();

        for (String table : tables) {
            Object[] conditions = new Object[4];
            conditions[0] = tableName;
            conditions[1] = duration.getStartTimeBucket();
            conditions[2] = duration.getEndTimeBucket();
            conditions[3] = instanceId;
            jdbcClient.executeQuery(
                "select " + Metrics.ENTITY_ID + ", " + ProcessRelationServerSideMetrics.COMPONENT_ID
                    + " from " + table
                    + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and "
                    + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=? and "
                    + ProcessRelationClientSideMetrics.SERVICE_INSTANCE_ID + "=?"
                    + " group by " + Metrics.ENTITY_ID + ", " + ProcessRelationServerSideMetrics.COMPONENT_ID,
                resultSet -> {
                    buildProcessCalls(resultSet, calls, detectPoint);
                    return null;
                },
                conditions
            );
        }

        return calls;
    }

    private void buildServiceCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                   DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            final IntList componentIds = new IntList(
                resultSet.getString(ServiceRelationServerSideMetrics.COMPONENT_IDS));
            for (int i = 0; i < componentIds.size(); i++) {
                Call.CallDetail call = new Call.CallDetail();
                call.buildFromServiceRelation(entityId, componentIds.get(i), detectPoint);
                calls.add(call);
            }
        }
    }

    private void buildInstanceCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                    DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            call.buildFromInstanceRelation(entityId, detectPoint);
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

    private void buildProcessCalls(ResultSet resultSet, List<Call.CallDetail> calls,
                                    DetectPoint detectPoint) throws SQLException {
        while (resultSet.next()) {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = resultSet.getString(Metrics.ENTITY_ID);
            int componentId = resultSet.getInt(ProcessRelationServerSideMetrics.COMPONENT_ID);
            call.buildProcessRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
    }
}
