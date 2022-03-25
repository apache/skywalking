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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.utils.IoTDBUtils;

@Slf4j
@RequiredArgsConstructor
public class IoTDBTopologyQueryDAO implements ITopologyQueryDAO {
    private final IoTDBClient client;

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB,
                                                                          List<String> serviceIds) throws IOException {
        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                serviceIds, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB,
                                                                         List<String> serviceIds) throws IOException {
        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID,
                serviceIds, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                new ArrayList<>(0), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID,
                new ArrayList<>(0), DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID,
                clientServiceId, serverServiceId, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID,
                clientServiceId, serverServiceId, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB, long endTB, String destEndpointId) throws IOException {
        List<Call.CallDetail> calls = loadEndpointFromSide(
                EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                EndpointRelationServerSideMetrics.DEST_ENDPOINT,
                destEndpointId, false);
        calls.addAll(loadEndpointFromSide(
                EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                EndpointRelationServerSideMetrics.DEST_ENDPOINT,
                destEndpointId, true));
        return calls;
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName, long startTB, long endTB,
                                                   String sourceCName, String destCName,
                                                   List<String> serviceIds, DetectPoint detectPoint) throws IOException {
        // This method don't use "group by" like other storage plugin.
        StringBuilder query = new StringBuilder();
        query.append("select ").append(ServiceRelationServerSideMetrics.COMPONENT_ID).append(" from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, tableName);
        IoTDBUtils.addQueryAsterisk(tableName, query);
        query.append(" where ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTB))
                .append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTB));
        if (serviceIds.size() > 0) {
            query.append(" and (");
            for (int i = 0; i < serviceIds.size(); i++) {
                query.append(sourceCName).append(" = \"").append(serviceIds.get(i))
                        .append("\" or ")
                        .append(destCName).append(" = \"").append(serviceIds.get(i)).append("\"");
                if (i != serviceIds.size() - 1) {
                    query.append(" or ");
                }
            }
            query.append(")");
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        List<Call.CallDetail> calls = new ArrayList<>();
        try {
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", query, wrapper.getColumnNames());
            }

            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                Call.CallDetail call = new Call.CallDetail();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String entityId = IoTDBUtils.layerName2IndexValue(layerNames[2]);
                final int componentId = fields.get(1).getIntV();
                call.buildFromServiceRelation(entityId, componentId, detectPoint);
                calls.add(call);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            if (wrapper != null) {
                sessionPool.closeResultSet(wrapper);
            }
        }
        return calls;
    }

    private List<Call.CallDetail> loadServiceInstanceCalls(String tableName, long startTB, long endTB,
                                                           String sourceCName, String descCName,
                                                           String sourceServiceId, String destServiceId,
                                                           DetectPoint detectPoint) throws IOException {
        // This method don't use "group by" like other storage plugin.
        StringBuilder query = new StringBuilder();
        query.append("select ").append(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID).append(" from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, tableName);
        IoTDBUtils.addQueryAsterisk(tableName, query);
        query.append(" where ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTB))
                .append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTB));
        query.append(" and ((").append(sourceCName).append(" = \"").append(sourceServiceId).append("\"")
                .append(" and ").append(descCName).append(" = \"").append(destServiceId).append("\"")
                .append(") or (").append(sourceCName).append(" = \"").append(destServiceId).append("\"")
                .append(" and ").append(descCName).append(" = \"").append(sourceServiceId).append(" \"))")
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        List<Call.CallDetail> calls = new ArrayList<>();
        try {
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", query, wrapper.getColumnNames());
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                Call.CallDetail call = new Call.CallDetail();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String entityId = IoTDBUtils.layerName2IndexValue(layerNames[2]);
                final int componentId = fields.get(1).getIntV();
                call.buildFromInstanceRelation(entityId, componentId, detectPoint);
                calls.add(call);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            if (wrapper != null) {
                sessionPool.closeResultSet(wrapper);
            }
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpointFromSide(String tableName, long startTB, long endTB,
                                                       String sourceCName, String destCName,
                                                       String id, boolean isSourceId) throws IOException {
        // This method don't use "group by" like other storage plugin.
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, tableName);
        IoTDBUtils.addQueryAsterisk(tableName, query);
        query.append(" where ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTB))
                .append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTB));
        query.append(" and ").append(isSourceId ? sourceCName : destCName).append(" = \"").append(id).append("\"")
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        List<Call.CallDetail> calls = new ArrayList<>();
        try {
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", query, wrapper.getColumnNames());
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                Call.CallDetail call = new Call.CallDetail();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String entityId = IoTDBUtils.layerName2IndexValue(layerNames[2]);
                call.buildFromEndpointRelation(entityId, DetectPoint.SERVER);
                calls.add(call);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            if (wrapper != null) {
                sessionPool.closeResultSet(wrapper);
            }
        }
        return calls;
    }
}
