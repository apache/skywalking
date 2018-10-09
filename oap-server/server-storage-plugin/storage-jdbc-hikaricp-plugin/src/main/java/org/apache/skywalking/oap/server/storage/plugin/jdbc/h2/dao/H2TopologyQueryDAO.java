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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceComponentIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceMappingIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationClientSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.ServiceComponent;
import org.apache.skywalking.oap.server.core.source.ServiceMapping;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.core.storage.TimePyramidTableNameBuilder;
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
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds, true);
    }

    @Override public List<Call> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds, false);
    }

    @Override public List<Call> loadServerSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, new ArrayList<>(0), false);
    }

    @Override public List<Call> loadClientSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return loadServiceCalls(tableName, startTB, endTB, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, new ArrayList<>(0), true);
    }

    @Override public List<ServiceMapping> loadServiceMappings(Step step, long startTB, long endTB) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMappingIndicator.INDEX_NAME);
        ResultSet resultSet = h2Client.executeQuery("select distinct " + ServiceMappingIndicator.SERVICE_ID
                + ", " + ServiceMappingIndicator.MAPPING_SERVICE_ID
                + " from " + tableName + " where "
                + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? ",
            startTB, endTB);
        List<ServiceMapping> serviceMappings = new ArrayList<>();
        try {
            while (resultSet.next()) {
                ServiceMapping serviceMapping = new ServiceMapping();
                serviceMapping.setServiceId(resultSet.getInt(ServiceMappingIndicator.SERVICE_ID));
                serviceMapping.setMappingServiceId(resultSet.getInt(ServiceMappingIndicator.MAPPING_SERVICE_ID));
                serviceMappings.add(serviceMapping);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return serviceMappings;
    }

    @Override
    public List<ServiceComponent> loadServiceComponents(Step step, long startTB, long endTB) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMappingIndicator.INDEX_NAME);
        ResultSet resultSet = h2Client.executeQuery("select distinct " + ServiceComponentIndicator.SERVICE_ID
                + ", " + ServiceComponentIndicator.COMPONENT_ID
                + " from " + tableName + " where "
                + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? ",
            startTB, endTB);
        List<ServiceComponent> serviceComponents = new ArrayList<>();
        try {
            while (resultSet.next()) {
                ServiceComponent serviceComponent = new ServiceComponent();
                serviceComponent.setServiceId(resultSet.getInt(ServiceComponentIndicator.SERVICE_ID));
                serviceComponent.setComponentId(resultSet.getInt(ServiceComponentIndicator.COMPONENT_ID));
                serviceComponents.add(serviceComponent);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return serviceComponents;
    }

    @Override public List<Call> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, EndpointRelationServerSideIndicator.INDEX_NAME);

        return loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideIndicator.SOURCE_ENDPOINT_ID, EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, destEndpointId, false);
    }

    @Override public List<Call> loadSpecifiedSourceOfClientSideEndpointRelations(Step step, long startTB,
        long endTB,
        int sourceEndpointId) throws IOException {
        String tableName = TimePyramidTableNameBuilder.build(step, EndpointRelationServerSideIndicator.INDEX_NAME);

        return loadEndpointFromSide(tableName, startTB, endTB, EndpointRelationServerSideIndicator.SOURCE_ENDPOINT_ID, EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, sourceEndpointId, true);
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
        ResultSet resultSet = h2Client.executeQuery("select distinct " + sourceCName
                + ", " + destCName
                + " from " + tableName + " where "
                + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? "
                + serviceIdMatchSql.toString(),
            conditions);
        List<Call> calls = new ArrayList<>();
        try {
            while (resultSet.next()) {
                Call call = new Call();
                call.setSource(resultSet.getInt(sourceCName));
                call.setTarget(resultSet.getInt(destCName));
                call.setId(ServiceRelation.buildEntityId(call.getSource(), call.getTarget()));
                call.setDetectPoint(isClientSide ? DetectPoint.CLIENT : DetectPoint.SERVER);
                calls.add(call);
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
        ResultSet resultSet = h2Client.executeQuery("select distinct " + sourceCName
                + ", " + destCName
                + " from " + tableName + " where "
                + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=? and"
                + (isSourceId ? sourceCName : destCName) + "=?",
            conditions);
        List<Call> calls = new ArrayList<>();
        try {
            while (resultSet.next()) {
                Call call = new Call();
                call.setSource(resultSet.getInt(sourceCName));
                call.setTarget(resultSet.getInt(destCName));
                call.setId(EndpointRelation.buildEntityId(call.getSource(), call.getTarget()));
                calls.add(call);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return calls;
    }
}
