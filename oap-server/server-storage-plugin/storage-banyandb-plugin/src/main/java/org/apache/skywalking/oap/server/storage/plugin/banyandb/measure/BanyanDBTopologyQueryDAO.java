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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
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
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import static java.util.Objects.nonNull;

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

        QueryBuilder<MeasureQuery> queryBuilder = buildServiceRelationsQuery(serviceIds);

        return queryServiceRelation(duration, queryBuilder, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                         List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        QueryBuilder<MeasureQuery> queryBuilder = buildServiceRelationsQuery(serviceIds);

        return queryServiceRelation(duration, queryBuilder, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException {
        return queryServiceRelation(duration, emptyMeasureQuery(), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException {
        return queryServiceRelation(duration, emptyMeasureQuery(), DetectPoint.CLIENT);
    }

    private QueryBuilder<MeasureQuery> buildServiceRelationsQuery(List<String> serviceIds) {
        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                query.or(in(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, serviceIds))
                        .or(in(ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds));
                query.groupBy(Sets.newHashSet(Metrics.ENTITY_ID, ServiceRelationServerSideMetrics.COMPONENT_IDS));
            }
        };
    }

    List<Call.CallDetail> queryServiceRelation(Duration duration,
                                               QueryBuilder<MeasureQuery> queryBuilder,
                                               DetectPoint detectPoint) throws IOException {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        TimestampRange timestampRange = null;
        if (startTB > 0 && endTB > 0) {
            timestampRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        final String modelName = detectPoint == DetectPoint.SERVER ? ServiceRelationServerSideMetrics.INDEX_NAME :
                ServiceRelationClientSideMetrics.INDEX_NAME;
        final Map<String, Call.CallDetail> callMap = new HashMap<>();
        MeasureQueryResponse resp = query(modelName,
                ImmutableSet.of(
                        ServiceRelationClientSideMetrics.COMPONENT_IDS,
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), timestampRange, queryBuilder
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        final Call.CallDetail call = new Call.CallDetail();
        final String entityId = resp.getDataPoints().get(0).getTagValue(Metrics.ENTITY_ID);
        final IntList componentIds = new IntList(
                resp.getDataPoints().get(0).getTagValue(ServiceRelationClientSideMetrics.COMPONENT_IDS));
        for (int i = 0; i < componentIds.size(); i++) {
            call.buildFromServiceRelation(entityId, componentIds.get(i), detectPoint);
            callMap.putIfAbsent(entityId, call);
        }
        return new ArrayList<>(callMap.values());
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        QueryBuilder<MeasureQuery> queryBuilder = buildInstanceRelationsQuery(
                clientServiceId, serverServiceId);
        return queryInstanceRelation(duration, queryBuilder, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        QueryBuilder<MeasureQuery> queryBuilder = buildInstanceRelationsQuery(
                clientServiceId, serverServiceId);
        return queryInstanceRelation(duration, queryBuilder, DetectPoint.CLIENT);
    }

    private QueryBuilder<MeasureQuery> buildInstanceRelationsQuery(String clientServiceId,
                                                                   String serverServiceId) {
        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                List<AbstractCriteria> instanceRelationsQueryConditions = new ArrayList<>(2);

                instanceRelationsQueryConditions.add(
                        // source_service_id = clientServiceId AND dest_service_id = serverServiceId
                        and(Lists.newArrayList(
                                eq(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, clientServiceId),
                                eq(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, serverServiceId))
                        ));
                instanceRelationsQueryConditions.add(
                        // source_service_id = clientServiceId AND dest_service_id = serverServiceId
                        and(Lists.newArrayList(
                                eq(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId),
                                eq(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, serverServiceId)
                        ))
                );
                query.criteria(or(instanceRelationsQueryConditions));
            }
        };
    }

    List<Call.CallDetail> queryInstanceRelation(Duration duration,
                                                QueryBuilder<MeasureQuery> queryBuilder,
                                                DetectPoint detectPoint) throws IOException {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        TimestampRange timestampRange = null;
        if (startTB > 0 && endTB > 0) {
            timestampRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        final String modelName = detectPoint == DetectPoint.SERVER ? ServiceInstanceRelationServerSideMetrics.INDEX_NAME :
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME;
        final Map<String, Call.CallDetail> callMap = new HashMap<>();
        MeasureQueryResponse resp = query(modelName,
                ImmutableSet.of(
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), timestampRange, queryBuilder
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        final Call.CallDetail call = new Call.CallDetail();
        final String entityId = resp.getDataPoints().get(0).getTagValue(Metrics.ENTITY_ID);
        call.buildFromInstanceRelation(entityId, detectPoint);
        callMap.putIfAbsent(entityId, call);
        return new ArrayList<>(callMap.values());
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(Duration duration, String destEndpointId) throws IOException {
        QueryBuilder<MeasureQuery> queryBuilder = buildEndpointRelationsQueries(destEndpointId);
        return queryEndpointRelation(duration, queryBuilder, DetectPoint.SERVER);
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

    private QueryBuilder<MeasureQuery> buildEndpointRelationsQueries(String destEndpointId) {
        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                query.or(eq(EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId))
                        .or(eq(EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId));
            }
        };
    }

    List<Call.CallDetail> queryEndpointRelation(Duration duration,
                                                QueryBuilder<MeasureQuery> queryBuilder,
                                                DetectPoint detectPoint) throws IOException {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        TimestampRange timestampRange = null;
        if (startTB > 0 && endTB > 0) {
            timestampRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        final Map<String, Call.CallDetail> callMap = new HashMap<>();
        MeasureQueryResponse resp = query(EndpointRelationServerSideMetrics.INDEX_NAME,
                ImmutableSet.of(
                        Metrics.ENTITY_ID
                ),
                Collections.emptySet(), timestampRange, queryBuilder
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        final Call.CallDetail call = new Call.CallDetail();
        final String entityId = resp.getDataPoints().get(0).getTagValue(Metrics.ENTITY_ID);
        call.buildFromEndpointRelation(entityId, detectPoint);
        callMap.putIfAbsent(entityId, call);
        return new ArrayList<>(callMap.values());
    }

    List<Call.CallDetail> queryProcessRelation(Duration duration,
                                               String serviceInstanceId,
                                               DetectPoint detectPoint) throws IOException {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        TimestampRange timestampRange = null;
        if (startTB > 0 && endTB > 0) {
            timestampRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        final String modelName = detectPoint == DetectPoint.SERVER ? ProcessRelationServerSideMetrics.INDEX_NAME :
                ProcessRelationClientSideMetrics.INDEX_NAME;
        final Map<String, Call.CallDetail> callMap = new HashMap<>();
        MeasureQueryResponse resp = query(modelName,
                ImmutableSet.of(
                        Metrics.ENTITY_ID, ProcessRelationClientSideMetrics.COMPONENT_ID),
                Collections.emptySet(), timestampRange, new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessRelationServerSideMetrics.SERVICE_INSTANCE_ID, serviceInstanceId));
                    }
                }
        );
        final Call.CallDetail call = new Call.CallDetail();
        final String entityId = resp.getDataPoints().get(0).getTagValue(Metrics.ENTITY_ID);
        final int componentId = ((Number) resp.getDataPoints()
                .get(0)
                .getTagValue(ProcessRelationClientSideMetrics.COMPONENT_ID)).intValue();
        call.buildProcessRelation(entityId, componentId, detectPoint);
        callMap.putIfAbsent(entityId, call);
        return new ArrayList<>(callMap.values());
    }
}
