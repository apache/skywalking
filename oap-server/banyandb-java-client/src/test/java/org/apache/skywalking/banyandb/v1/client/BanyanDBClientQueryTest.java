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

package org.apache.skywalking.banyandb.v1.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;
import org.apache.skywalking.banyandb.v1.trace.TraceServiceGrpc;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class BanyanDBClientQueryTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private final TraceServiceGrpc.TraceServiceImplBase serviceImpl =
            mock(TraceServiceGrpc.TraceServiceImplBase.class, delegatesTo(
                    new TraceServiceGrpc.TraceServiceImplBase() {
                        @Override
                        public void query(BanyandbTrace.QueryRequest request, StreamObserver<BanyandbTrace.QueryResponse> responseObserver) {
                            responseObserver.onNext(BanyandbTrace.QueryResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }
                    }));

    private BanyanDBClient client;

    @Before
    public void setUp() throws IOException {
        client = new BanyanDBClient("127.0.0.1", 17912, "default");

        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(serviceImpl).build().start());

        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        client.connect(channel);
    }

    @Test
    public void testNonNull() {
        Assert.assertNotNull(this.client);
    }

    @Test
    public void testQuery_tableScan() {
        ArgumentCaptor<BanyandbTrace.QueryRequest> requestCaptor = ArgumentCaptor.forClass(BanyandbTrace.QueryRequest.class);

        Instant end = Instant.now();
        Instant begin = end.minus(15, ChronoUnit.MINUTES);
        TraceQuery query = new TraceQuery("sw",
                new TimestampRange(begin.toEpochMilli(), end.toEpochMilli()),
                Arrays.asList("state", "start_time", "duration", "trace_id"));
        // search for all states
        query.appendCondition(PairQueryCondition.LongQueryCondition.eq("state", 0L));
        query.setOrderBy(new TraceQuery.OrderBy("duration", TraceQuery.OrderBy.Type.DESC));
        client.queryTraces(query);

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());

        final BanyandbTrace.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals("sw", request.getMetadata().getName());
        Assert.assertEquals("default", request.getMetadata().getGroup());
        // assert timeRange, both seconds and the nanos
        Assert.assertEquals(begin.toEpochMilli() / 1000, request.getTimeRange().getBegin().getSeconds());
        Assert.assertEquals(TimeUnit.MILLISECONDS.toNanos(begin.toEpochMilli() % 1000), request.getTimeRange().getBegin().getNanos());
        Assert.assertEquals(end.toEpochMilli() / 1000, request.getTimeRange().getEnd().getSeconds());
        Assert.assertEquals(TimeUnit.MILLISECONDS.toNanos(end.toEpochMilli() % 1000), request.getTimeRange().getEnd().getNanos());
        // assert fields, we only have state as a condition which should be state
        Assert.assertEquals(1, request.getFieldsCount());
        // assert orderBy, by default DESC
        Assert.assertEquals(Banyandb.QueryOrder.Sort.SORT_DESC, request.getOrderBy().getSort());
        Assert.assertEquals("duration", request.getOrderBy().getKeyName());
        // assert state
        Assert.assertEquals(Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ, request.getFields(0).getOp());
        Assert.assertEquals(0, request.getFields(0).getCondition().getIntPair().getValue());
        // assert projections
        assertCollectionEqual(Lists.newArrayList("duration", "state", "start_time", "trace_id"), request.getProjection().getKeyNamesList());
    }

    @Test
    public void testQuery_indexScan() {
        ArgumentCaptor<BanyandbTrace.QueryRequest> requestCaptor = ArgumentCaptor.forClass(BanyandbTrace.QueryRequest.class);
        Instant begin = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant end = Instant.now();
        String serviceId = "service_id_b";
        String serviceInstanceId = "service_id_b_1";
        String endpointId = "/check_0";
        long minDuration = 10;
        long maxDuration = 100;

        TraceQuery query = new TraceQuery("sw",
                new TimestampRange(begin.toEpochMilli(), end.toEpochMilli()),
                Arrays.asList("state", "start_time", "duration", "trace_id"));
        // search for the successful states
        query.appendCondition(PairQueryCondition.LongQueryCondition.eq("state", 1L))
                .appendCondition(PairQueryCondition.StringQueryCondition.eq("service_id", serviceId))
                .appendCondition(PairQueryCondition.StringQueryCondition.eq("service_instance_id", serviceInstanceId))
                .appendCondition(PairQueryCondition.StringQueryCondition.eq("endpoint_id", endpointId))
                .appendCondition(PairQueryCondition.LongQueryCondition.ge("duration", minDuration))
                .appendCondition(PairQueryCondition.LongQueryCondition.le("duration", maxDuration))
                .setOrderBy(new TraceQuery.OrderBy("start_time", TraceQuery.OrderBy.Type.ASC));

        client.queryTraces(query);

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());
        final BanyandbTrace.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals("sw", request.getMetadata().getName());
        Assert.assertEquals("default", request.getMetadata().getGroup());
        // assert timeRange
        Assert.assertEquals(begin.getEpochSecond(), request.getTimeRange().getBegin().getSeconds());
        Assert.assertEquals(end.getEpochSecond(), request.getTimeRange().getEnd().getSeconds());
        // assert fields, we only have state as a condition
        Assert.assertEquals(6, request.getFieldsCount());
        // assert orderBy, by default DESC
        Assert.assertEquals(Banyandb.QueryOrder.Sort.SORT_ASC, request.getOrderBy().getSort());
        Assert.assertEquals("start_time", request.getOrderBy().getKeyName());
        // assert projections
        assertCollectionEqual(Lists.newArrayList("duration", "state", "start_time", "trace_id"), request.getProjection().getKeyNamesList());
        // assert fields
        assertCollectionEqual(request.getFieldsList(), ImmutableList.of(
                PairQueryCondition.LongQueryCondition.ge("duration", minDuration).build(), // 1 -> duration >= minDuration
                PairQueryCondition.LongQueryCondition.le("duration", maxDuration).build(), // 2 -> duration <= maxDuration
                PairQueryCondition.StringQueryCondition.eq("service_id", serviceId).build(), // 3 -> service_id
                PairQueryCondition.StringQueryCondition.eq("service_instance_id", serviceInstanceId).build(), // 4 -> service_instance_id
                PairQueryCondition.StringQueryCondition.eq("endpoint_id", endpointId).build(), // 5 -> endpoint_id
                PairQueryCondition.LongQueryCondition.eq("state", 1L).build() // 7 -> state
        ));
    }

    @Test
    public void testQuery_traceIDFetch() {
        ArgumentCaptor<BanyandbTrace.QueryRequest> requestCaptor = ArgumentCaptor.forClass(BanyandbTrace.QueryRequest.class);
        String traceId = "1111.222.333";

        TraceQuery query = new TraceQuery("sw", Arrays.asList("state", "start_time", "duration", "trace_id"));
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("trace_id", traceId));

        client.queryTraces(query);

        verify(serviceImpl).query(requestCaptor.capture(), ArgumentMatchers.any());
        final BanyandbTrace.QueryRequest request = requestCaptor.getValue();
        // assert metadata
        Assert.assertEquals("sw", request.getMetadata().getName());
        Assert.assertEquals("default", request.getMetadata().getGroup());
        Assert.assertEquals(1, request.getFieldsCount());
        // assert fields
        assertCollectionEqual(request.getFieldsList(), ImmutableList.of(
                PairQueryCondition.StringQueryCondition.eq("trace_id", traceId).build()
        ));
    }

    @Test
    public void testQuery_responseConversion() {
        final byte[] binaryData = new byte[]{13};
        final String segmentId = "1231.dfd.123123ssf";
        final String traceId = "trace_id-xxfff.111323";
        final long duration = 200L;
        final Instant now = Instant.now();
        final BanyandbTrace.QueryResponse responseObj = BanyandbTrace.QueryResponse.newBuilder()
                .addEntities(BanyandbTrace.Entity.newBuilder()
                        .setDataBinary(ByteString.copyFrom(binaryData))
                        .setEntityId(segmentId)
                        .setTimestamp(Timestamp.newBuilder()
                                .setSeconds(now.toEpochMilli() / 1000)
                                .setNanos((int) TimeUnit.MILLISECONDS.toNanos(now.toEpochMilli() % 1000))
                                .build())
                        .addFields(Banyandb.TypedPair.newBuilder()
                                .setKey("trace_id")
                                .setStrPair(Banyandb.Str.newBuilder().setValue(traceId).build()).build())
                        .addFields(Banyandb.TypedPair.newBuilder()
                                .setKey("duration")
                                .setIntPair(Banyandb.Int.newBuilder().setValue(duration).build()).build())
                        .addFields(Banyandb.TypedPair.newBuilder()
                                .setKey("mq.broker")
                                .setNullPair(Banyandb.TypedPair.NullWithType.newBuilder().setType(Banyandb.FieldType.FIELD_TYPE_STRING).build()).build())
                        .build())
                .build();
        TraceQueryResponse resp = new TraceQueryResponse(responseObj);
        Assert.assertNotNull(resp);
        Assert.assertEquals(1, resp.getEntities().size());
        Assert.assertEquals(3, resp.getEntities().get(0).getFields().size());
        Assert.assertEquals(3, resp.getEntities().get(0).getFields().size());
        Assert.assertEquals(new FieldAndValue.StringFieldPair("trace_id", traceId), resp.getEntities().get(0).getFields().get(0));
        Assert.assertEquals(new FieldAndValue.LongFieldPair("duration", duration), resp.getEntities().get(0).getFields().get(1));
        Assert.assertEquals(new FieldAndValue.StringFieldPair("mq.broker", null), resp.getEntities().get(0).getFields().get(2));
    }

    static <T> void assertCollectionEqual(Collection<T> c1, Collection<T> c2) {
        Assert.assertTrue(c1.size() == c2.size() && c1.containsAll(c2) && c2.containsAll(c1));
    }
}
