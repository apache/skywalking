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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.TraceServiceGrpc;
import org.apache.skywalking.banyandb.client.request.TraceSearchQuery;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class BanyanDBTraceQueryDAOTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private final TraceServiceGrpc.TraceServiceImplBase serviceImpl =
            mock(TraceServiceGrpc.TraceServiceImplBase.class, delegatesTo(
                    new TraceServiceGrpc.TraceServiceImplBase() {
                        @Override
                        public void query(Query.QueryRequest request, StreamObserver<Query.QueryResponse> responseObserver) {
                            responseObserver.onNext(Query.QueryResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }
                    }));

    private BanyanDBTraceQueryDAO queryDAO;

    @Before
    public void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(serviceImpl).build().start());

        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        BanyanDBClient client = new BanyanDBClient(channel);

        // Create a BanyanDBTraceQueryDAO using the in-process channel;
        queryDAO = new BanyanDBTraceQueryDAO(client);
    }

    @Test
    public void query_testTableScan() throws IOException {
        ArgumentCaptor<Query.QueryRequest> requestCaptor = ArgumentCaptor.forClass(Query.QueryRequest.class);

        Instant begin = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant end = Instant.now();
        queryDAO.queryBasicTraces(begin.getEpochSecond(),
                end.getEpochSecond(),
                0,
                0,
                null,
                null,
                null,
                null,
                10,
                0,
                TraceState.ALL,
                QueryOrder.BY_DURATION,
                Collections.emptyList());

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());
        final Query.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals(BanyanDBSchema.NAME, request.getMetadata().getName());
        Assert.assertEquals(BanyanDBSchema.GROUP, request.getMetadata().getGroup());
        // assert timeRange
        Assert.assertEquals(begin.getEpochSecond(), request.getTimeRange().getBegin().getSeconds());
        Assert.assertEquals(end.getEpochSecond(), request.getTimeRange().getEnd().getSeconds());
        // assert fields, we only have state as a condition
        Assert.assertEquals(1, request.getFieldsCount());
        // assert orderBy, by default DESC
        Assert.assertEquals(Query.QueryOrder.Sort.SORT_DESC, request.getOrderBy().getSort());
        Assert.assertEquals("duration", request.getOrderBy().getKeyName());
        // assert state
        Assert.assertEquals(Query.PairQuery.BinaryOp.BINARY_OP_EQ, request.getFields(0).getOp());
        Assert.assertEquals(0, request.getFields(0).getCondition().getIntPair().getValues(0));
        // assert projections
        assertCollectionEqual(Lists.newArrayList("duration", "state", "start_time", "trace_id"), request.getProjection().getKeyNamesList());
    }

    @Test
    public void query_testIndexScan() throws IOException {
        ArgumentCaptor<Query.QueryRequest> requestCaptor = ArgumentCaptor.forClass(Query.QueryRequest.class);

        Instant begin = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant end = Instant.now();
        String serviceId = "service_id_b";
        String serviceInstanceId = "service_id_b_1";
        String endpointId = "/check_0";
        long minDuration = 10;
        long maxDuration = 100;
        queryDAO.queryBasicTraces(begin.getEpochSecond(),
                end.getEpochSecond(),
                minDuration,
                maxDuration,
                serviceId,
                serviceInstanceId,
                endpointId,
                "trace_id",
                10,
                0,
                TraceState.SUCCESS,
                QueryOrder.BY_START_TIME,
                Collections.emptyList());

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());
        final Query.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals(BanyanDBSchema.NAME, request.getMetadata().getName());
        Assert.assertEquals(BanyanDBSchema.GROUP, request.getMetadata().getGroup());
        // assert timeRange
        Assert.assertEquals(begin.getEpochSecond(), request.getTimeRange().getBegin().getSeconds());
        Assert.assertEquals(end.getEpochSecond(), request.getTimeRange().getEnd().getSeconds());
        // assert fields, we only have state as a condition
        Assert.assertEquals(6, request.getFieldsCount());
        // assert orderBy, by default DESC
        Assert.assertEquals(Query.QueryOrder.Sort.SORT_DESC, request.getOrderBy().getSort());
        Assert.assertEquals("start_time", request.getOrderBy().getKeyName());
        // assert projections
        assertCollectionEqual(Lists.newArrayList("duration", "state", "start_time", "trace_id"), request.getProjection().getKeyNamesList());
        // assert fields
        assertCollectionEqual(request.getFieldsList(), ImmutableList.of(
                TraceSearchQuery.ge("duration", minDuration).toPairQuery(), // 1 -> duration >= minDuration
                TraceSearchQuery.le("duration", maxDuration).toPairQuery(), // 2 -> duration <= maxDuration
                TraceSearchQuery.eq("service_id", serviceId).toPairQuery(), // 3 -> service_id
                TraceSearchQuery.eq("service_instance_id", serviceInstanceId).toPairQuery(), // 4 -> service_instance_id
                TraceSearchQuery.eq("endpoint_id", endpointId).toPairQuery(), // 5 -> endpoint_id
                TraceSearchQuery.eq("state", BanyanDBSchema.TraceState.SUCCESS.getState()).toPairQuery() // 7 -> state
        ));
    }

    @Test
    public void query_testTraceIDFetch() throws IOException {
        ArgumentCaptor<Query.QueryRequest> requestCaptor = ArgumentCaptor.forClass(Query.QueryRequest.class);
        String traceId = "1111.222.333";
        queryDAO.queryByTraceId(traceId);

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());
        final Query.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals(BanyanDBSchema.NAME, request.getMetadata().getName());
        Assert.assertEquals(BanyanDBSchema.GROUP, request.getMetadata().getGroup());
        Assert.assertEquals(1, request.getFieldsCount());
        // assert fields
        assertCollectionEqual(request.getFieldsList(), ImmutableList.of(
                TraceSearchQuery.eq("trace_id", traceId).toPairQuery()
        ));
        // assert projections
        Set<String> projections = new HashSet<>(BanyanDBSchema.FIELD_NAMES);
        projections.add("data_binary");
        assertCollectionEqual(projections, request.getProjection().getKeyNamesList());
    }

    static <T> void assertCollectionEqual(Collection<T> c1, Collection<T> c2) {
        Assert.assertTrue(c1.size() == c2.size() && c1.containsAll(c2) && c2.containsAll(c1));
    }
}
