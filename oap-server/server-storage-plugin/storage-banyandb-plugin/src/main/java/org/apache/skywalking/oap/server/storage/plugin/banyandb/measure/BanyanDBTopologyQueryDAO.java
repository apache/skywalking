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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
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
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.Conditions;

public class BanyanDBTopologyQueryDAO extends AbstractBanyanDBDAO implements ITopologyQueryDAO {

    public BanyanDBTopologyQueryDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration,
                                                                          List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return queryServiceRelation(duration, serviceRelationsWhere(serviceIds), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                         List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return queryServiceRelation(duration, serviceRelationsWhere(serviceIds), DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException {
        return queryServiceRelation(duration, Conditions.create(), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException {
        return queryServiceRelation(duration, Conditions.create(), DetectPoint.CLIENT);
    }

    private Conditions serviceRelationsWhere(List<String> serviceIds) {
        return Conditions.create().or(List.of(
                Conditions.group().in(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, serviceIds),
                Conditions.group().in(ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds)))
                .groupBy(Metrics.ENTITY_ID, ServiceRelationServerSideMetrics.COMPONENT_IDS);
    }

    List<Call.CallDetail> queryServiceRelation(Duration duration,
                                               Conditions where,
                                               DetectPoint detectPoint) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final String modelName = detectPoint == DetectPoint.SERVER ? ServiceRelationServerSideMetrics.INDEX_NAME :
                ServiceRelationClientSideMetrics.INDEX_NAME;
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(modelName, duration.getStep());
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema,
                ImmutableSet.of(
                        ServiceRelationClientSideMetrics.COMPONENT_IDS,
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), getTimestampRange(duration), where
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        List<Call.CallDetail> calls = new ArrayList<>(resp.size());
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            final String entityId = dataPoint.getTagValue(Metrics.ENTITY_ID);
            final IntList componentIds = new IntList(
                    dataPoint.getTagValue(ServiceRelationClientSideMetrics.COMPONENT_IDS));
            for (int i = 0; i < componentIds.size(); i++) {
                final Call.CallDetail call = new Call.CallDetail();
                call.buildFromServiceRelation(entityId, componentIds.get(i), detectPoint);
                calls.add(call);
            }
        }
        return calls;
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return queryInstanceRelation(duration, instanceRelationsWhere(clientServiceId, serverServiceId),
                DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return queryInstanceRelation(duration, instanceRelationsWhere(clientServiceId, serverServiceId),
                DetectPoint.CLIENT);
    }

    private Conditions instanceRelationsWhere(String clientServiceId, String serverServiceId) {
        // (source_service_id = clientServiceId AND dest_service_id = serverServiceId)
        //   OR (dest_service_id = clientServiceId AND source_service_id = serverServiceId)
        return Conditions.create().or(List.of(
                Conditions.group()
                        .eq(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, clientServiceId)
                        .eq(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, serverServiceId),
                Conditions.group()
                        .eq(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId)
                        .eq(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, serverServiceId)));
    }

    List<Call.CallDetail> queryInstanceRelation(Duration duration,
                                                Conditions where,
                                                DetectPoint detectPoint) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final String modelName = detectPoint == DetectPoint.SERVER ? ServiceInstanceRelationServerSideMetrics.INDEX_NAME :
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME;
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(modelName, duration.getStep());
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema,
                ImmutableSet.of(
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), getTimestampRange(duration), where
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        List<Call.CallDetail> calls = new ArrayList<>(resp.size());
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            final Call.CallDetail call = new Call.CallDetail();
            final String entityId = dataPoint.getTagValue(Metrics.ENTITY_ID);
            call.buildFromInstanceRelation(entityId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(Duration duration, String destEndpointId) throws IOException {
        return queryEndpointRelation(duration, destEndpointId, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId,
                                                                         Duration duration) throws IOException {
        return queryProcessRelation(duration, serviceInstanceId, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId,
                                                                         Duration duration) throws IOException {
        return queryProcessRelation(duration, serviceInstanceId, DetectPoint.SERVER);
    }

    List<Call.CallDetail> queryEndpointRelation(Duration duration,
                                                String destEndpointId,
                                                DetectPoint detectPoint) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(EndpointRelationServerSideMetrics.INDEX_NAME, duration.getStep());
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema,
                ImmutableSet.of(
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), getTimestampRange(duration),
                Conditions.create().or(List.of(
                        Conditions.group().eq(EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId),
                        Conditions.group().eq(EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId)))
                        .groupBy(Metrics.ENTITY_ID)
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        List<Call.CallDetail> resultSet = new ArrayList<>(resp.size());
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            final Call.CallDetail call = new Call.CallDetail();
            final String entityId = dataPoint.getTagValue(Metrics.ENTITY_ID);
            call.buildFromEndpointRelation(entityId, detectPoint);
            resultSet.add(call);
        }
        return resultSet;
    }

    List<Call.CallDetail> queryProcessRelation(Duration duration,
                                               String serviceInstanceId,
                                               DetectPoint detectPoint) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final String modelName = detectPoint == DetectPoint.SERVER ? ProcessRelationServerSideMetrics.INDEX_NAME :
                ProcessRelationClientSideMetrics.INDEX_NAME;
        // process relation only has minute data
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(modelName, DownSampling.Minute);
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema,
                ImmutableSet.of(Metrics.ENTITY_ID, ProcessRelationClientSideMetrics.COMPONENT_ID),
                Collections.emptySet(), getTimestampRange(duration),
                Conditions.create()
                        .eq(ProcessRelationServerSideMetrics.SERVICE_INSTANCE_ID, serviceInstanceId)
                        .groupBy(Metrics.ENTITY_ID, ProcessRelationClientSideMetrics.COMPONENT_ID)
        );

        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        List<Call.CallDetail> calls = new ArrayList<>(resp.size());
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            final String entityId = dataPoint.getTagValue(Metrics.ENTITY_ID);
            final Number componentIdNumber = dataPoint.getTagValue(ProcessRelationClientSideMetrics.COMPONENT_ID);
            final int componentId = componentIdNumber.intValue();
            Call.CallDetail call = new Call.CallDetail();
            call.buildProcessRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }

        return calls;
    }
}
