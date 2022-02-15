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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.vavr.Tuple2;
import io.vavr.Tuple4;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBTopologyQueryDAO extends AbstractBanyanDBDAO implements ITopologyQueryDAO {
    public BanyanDBTopologyQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID, Collections.emptyList(), DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID, Collections.emptyList(), DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
                DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
                DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB, long endTB, String destEndpointId) throws IOException {
        return loadEndpointCalls(
                EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId
        );
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName,
                                                   long startTB,
                                                   long endTB,
                                                   String sourceCName,
                                                   String destCName,
                                                   List<String> serviceIds,
                                                   DetectPoint detectPoint) throws IOException {
        TimestampRange timeRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        List<Call.CallDetail> calls = new ArrayList<>();
        if (serviceIds.isEmpty()) {
            StreamQueryResponse resp = query(tableName, Collections.emptyList(), timeRange, new QueryBuilder() {
                @Override
                void apply(StreamQuery query) {
                    // query component_id
                    query.setDataProjections(Collections.singletonList(ServiceRelationServerSideMetrics.COMPONENT_ID));
                }
            });

            calls.addAll(resp.getElements().stream().map(new ServiceCallDetailDeserializer(detectPoint)).collect(Collectors.toList()));
        } else {
            for (String fieldOfInterest : ImmutableList.of(sourceCName, destCName)) {
                for (String serviceID : serviceIds) {
                    StreamQueryResponse resp = query(tableName, ImmutableList.of(fieldOfInterest), timeRange, new QueryBuilder() {
                        @Override
                        void apply(StreamQuery query) {
                            // query component_id
                            query.setDataProjections(Collections.singletonList(ServiceRelationServerSideMetrics.COMPONENT_ID));

                            query.appendCondition(eq(fieldOfInterest, serviceID));
                        }
                    });

                    calls.addAll(resp.getElements().stream().map(new ServiceCallDetailDeserializer(detectPoint)).collect(Collectors.toList()));
                }
            }
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
        TimestampRange timeRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));

        Set<Tuple4<String, String, String, String>> productQuerySet = ImmutableSet.of(
                new Tuple4<>(sourceCName, sourceServiceId, descCName, destServiceId),
                new Tuple4<>(sourceCName, destServiceId, descCName, sourceServiceId)
        );

        List<Call.CallDetail> calls = new ArrayList<>();

        for (Tuple4<String, String, String, String> querySet : productQuerySet) {
            StreamQueryResponse resp = query(tableName, ImmutableList.of(querySet._1(), querySet._3()), timeRange, new QueryBuilder() {
                @Override
                void apply(StreamQuery query) {
                    // query component_id
                    query.setDataProjections(Collections.singletonList(ServiceRelationServerSideMetrics.COMPONENT_ID));

                    query.appendCondition(eq(querySet._1(), querySet._2()));
                    query.appendCondition(eq(querySet._3(), querySet._4()));
                }
            });

            calls.addAll(resp.getElements().stream().map(new InstanceCallDetailDeserializer(detectPoint)).collect(Collectors.toList()));
        }

        return calls;
    }

    private List<Call.CallDetail> loadEndpointCalls(String tableName,
                                                    long startTB,
                                                    long endTB,
                                                    String sourceCName,
                                                    String destCName,
                                                    String id) throws IOException {
        TimestampRange timeRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));

        Set<Tuple2<String, String>> allPossibleQuerySet = ImmutableSet.of(
                new Tuple2<>(sourceCName, id),
                new Tuple2<>(destCName, id)
        );

        List<Call.CallDetail> calls = new ArrayList<>();

        for (Tuple2<String, String> querySet : allPossibleQuerySet) {
            StreamQueryResponse resp = query(tableName, ImmutableList.of(querySet._1()), timeRange, new QueryBuilder() {
                @Override
                void apply(StreamQuery query) {
                    query.appendCondition(eq(querySet._1(), querySet._2()));
                }
            });

            calls.addAll(resp.getElements().stream().map(new EndpointCallDetailDeserializer()).collect(Collectors.toList()));
        }

        return calls;
    }

    @RequiredArgsConstructor
    public static class ServiceCallDetailDeserializer implements RowEntityDeserializer<Call.CallDetail> {
        private final DetectPoint detectPoint;

        @Override
        public Call.CallDetail apply(RowEntity rowEntity) {
            Call.CallDetail call = new Call.CallDetail();
            String[] idsSlice = rowEntity.getId().split(Const.ID_CONNECTOR);
            String entityId = idsSlice[1];
            int componentId = ((Number) rowEntity.getTagFamilies().get(1) // Tag Family: "data"
                    .get(0).getValue()).intValue();
            call.buildFromServiceRelation(entityId, componentId, this.detectPoint);
            return call;
        }
    }

    @RequiredArgsConstructor
    public static class InstanceCallDetailDeserializer implements RowEntityDeserializer<Call.CallDetail> {
        private final DetectPoint detectPoint;

        @Override
        public Call.CallDetail apply(RowEntity rowEntity) {
            Call.CallDetail call = new Call.CallDetail();
            String[] idsSlice = rowEntity.getId().split(Const.ID_CONNECTOR);
            String entityId = idsSlice[1];
            int componentId = ((Number) rowEntity.getTagFamilies().get(1) // Tag Family: "data"
                    .get(0).getValue()).intValue();
            call.buildFromInstanceRelation(entityId, componentId, this.detectPoint);
            return call;
        }
    }

    @RequiredArgsConstructor
    public static class EndpointCallDetailDeserializer implements RowEntityDeserializer<Call.CallDetail> {
        @Override
        public Call.CallDetail apply(RowEntity rowEntity) {
            Call.CallDetail call = new Call.CallDetail();
            String[] idsSlice = rowEntity.getId().split(Const.ID_CONNECTOR);
            String entityId = idsSlice[1];
            call.buildFromEndpointRelation(entityId, DetectPoint.SERVER);
            return call;
        }
    }
}
