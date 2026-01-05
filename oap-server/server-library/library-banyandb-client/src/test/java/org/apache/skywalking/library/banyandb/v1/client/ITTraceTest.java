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

package org.apache.skywalking.library.banyandb.v1.client;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.util.TimeUtils;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient.DEFAULT_EXPIRE_AT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Integration test for trace functionality.
 * Note: This test demonstrates the trace API but requires a running BanyanDB instance.
 */
public class ITTraceTest extends BanyanDBClientTestCI {
    private final String groupName = "sw_trace";
    private final String traceName = "trace_data";

    @BeforeEach
    public void setUp() throws IOException, BanyanDBException, InterruptedException {
        this.setUpConnection();
        // Create trace group
        BanyandbCommon.Group traceGroup = BanyandbCommon.Group.newBuilder()
                                                              .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                                                  .setName(groupName))
                                                              .setCatalog(BanyandbCommon.Catalog.CATALOG_TRACE)
                                                              .setResourceOpts(BanyandbCommon.ResourceOpts.newBuilder()
                                                                                                          .setShardNum(
                                                                                                              2)
                                                                                                          .setSegmentInterval(
                                                                                                              BanyandbCommon.IntervalRule.newBuilder()
                                                                                                                                         .setUnit(
                                                                                                                                             BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                                                         .setNum(
                                                                                                                                             1))
                                                                                                          .setTtl(
                                                                                                              BanyandbCommon.IntervalRule.newBuilder()
                                                                                                                                         .setUnit(
                                                                                                                                             BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                                                         .setNum(
                                                                                                                                             7)))
                                                              .build();

        this.client.define(traceGroup);

        // Create trace schema
        BanyandbDatabase.Trace trace = BanyandbDatabase.Trace.newBuilder()
                                                             .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                                                 .setGroup(groupName)
                                                                                                 .setName(traceName))
                                                             .addTags(BanyandbDatabase.TraceTagSpec.newBuilder()
                                                                                                   .setName("trace_id")
                                                                                                   .setType(
                                                                                                       BanyandbDatabase.TagType.TAG_TYPE_STRING))
                                                             .addTags(BanyandbDatabase.TraceTagSpec.newBuilder()
                                                                                                   .setName("span_id")
                                                                                                   .setType(
                                                                                                       BanyandbDatabase.TagType.TAG_TYPE_STRING))
                                                             .addTags(BanyandbDatabase.TraceTagSpec.newBuilder()
                                                                                                   .setName(
                                                                                                       "service_name")
                                                                                                   .setType(
                                                                                                       BanyandbDatabase.TagType.TAG_TYPE_STRING))
                                                             .addTags(BanyandbDatabase.TraceTagSpec.newBuilder()
                                                                                                   .setName(
                                                                                                       "start_time")
                                                                                                   .setType(
                                                                                                       BanyandbDatabase.TagType.TAG_TYPE_TIMESTAMP))
                                                             .setTraceIdTagName("trace_id")
                                                             .setSpanIdTagName("span_id")
                                                             .setTimestampTagName("start_time")
                                                             .build();

        this.client.define(trace);
        this.client.define(buildIndexRule());
        this.client.define(buildIndexRuleBinding());
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.closeClient();
    }

    @Test
    public void testTraceSchemaOperations() throws BanyanDBException {
        // Test trace definition exists
        BanyandbDatabase.Trace retrievedTrace = client.findTrace(groupName, traceName);
        assertNotNull(retrievedTrace);
        assertEquals(traceName, retrievedTrace.getMetadata().getName());
        assertEquals(groupName, retrievedTrace.getMetadata().getGroup());

        // Test trace exists
        assertTrue(client.existTrace(groupName, traceName).isHasResource());
    }

    @Test
    public void testTraceQueryByTraceId() throws BanyanDBException, ExecutionException, InterruptedException, TimeoutException {
        // Test data
        String traceId = "trace-query-test-12345";
        String spanId = "span-query-test-67890";
        String serviceName = "query-test-service";
        Instant now = Instant.now();
        byte[] spanData = "query-test-span-data".getBytes();

        // Create and write trace data
        TraceWrite traceWrite = client.createTraceWrite(groupName, traceName)
                                      .tag("trace_id", Value.stringTagValue(traceId))
                                      .tag("span_id", Value.stringTagValue(spanId))
                                      .tag("service_name", Value.stringTagValue(serviceName))
                                      .tag("start_time", Value.timestampTagValue(now.toEpochMilli()))
                                      .span(spanData)
                                      .version(1L);

        StreamObserver<BanyandbTrace.WriteRequest> writeObserver
            = client.getTraceServiceStub().write(new StreamObserver<BanyandbTrace.WriteResponse>() {
            @Override
            public void onNext(BanyandbTrace.WriteResponse writeResponse) {
                assertEquals(BanyandbModel.Status.STATUS_SUCCEED.name(), writeResponse.getStatus());
            }

            @Override
            public void onError(Throwable throwable) {
                fail("write failed: " + throwable.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });
        try {
            writeObserver.onNext(traceWrite.build());
        } finally {
            writeObserver.onCompleted();
        }

        // Create trace query with trace_id condition
        TraceQuery query = new TraceQuery(
            Lists.newArrayList(groupName),
            traceName,
            Collections.emptySet()
        );
        query.and(PairQueryCondition.StringQueryCondition.eq("trace_id", traceId));

        // Execute query with conditions
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TraceQueryResponse response = client.query(query);
            assertNotNull(response);
            assertFalse(response.isEmpty());
            assertEquals(1, response.size());

            // Verify we can access trace data
            assertNotNull(response.getTraces());
            assertEquals(1, response.getTraces().size());

            // Get the first trace and verify its contents
            BanyandbTrace.Trace trace = response.getTraces().get(0);
            assertNotNull(trace);
            assertEquals(1, trace.getSpansCount());

            // Get the span from the trace and verify its contents
            BanyandbTrace.Span span = trace.getSpans(0);
            assertNotNull(span);

            // Verify span data (binary content) - this is the main content returned
            assertNotNull(span.getSpan());
            assertFalse(span.getSpan().isEmpty());
            assertArrayEquals(spanData, span.getSpan().toByteArray());
        });
    }

    @Test
    public void testTraceQueryOrderByStartTime() throws BanyanDBException, ExecutionException, InterruptedException, TimeoutException {
        // Test data with different timestamps
        String traceId = "trace-order-test-";
        String serviceName = "order-test-service";
        Instant baseTime = Instant.now().minusSeconds(60); // Start 1 minute ago

        // Create 3 traces with different timestamps (1 minute apart)
        TraceWrite trace1 = client.createTraceWrite(groupName, traceName)
                                  .tag("trace_id", Value.stringTagValue(traceId + "1"))
                                  .tag("span_id", Value.stringTagValue("span-1"))
                                  .tag("service_name", Value.stringTagValue(serviceName))
                                  .tag("start_time", Value.timestampTagValue(baseTime.toEpochMilli()))
                                  .span("span-data-1".getBytes())
                                  .version(1L);

        TraceWrite trace2 = client.createTraceWrite(groupName, traceName)
                                  .tag("trace_id", Value.stringTagValue(traceId + "2"))
                                  .tag("span_id", Value.stringTagValue("span-2"))
                                  .tag("service_name", Value.stringTagValue(serviceName))
                                  .tag("start_time", Value.timestampTagValue(baseTime.plusSeconds(60).toEpochMilli()))
                                  .span("span-data-2".getBytes())
                                  .version(1L);

        TraceWrite trace3 = client.createTraceWrite(groupName, traceName)
                                  .tag("trace_id", Value.stringTagValue(traceId + "3"))
                                  .tag("span_id", Value.stringTagValue("span-3"))
                                  .tag("service_name", Value.stringTagValue(serviceName))
                                  .tag("start_time", Value.timestampTagValue(baseTime.plusSeconds(120).toEpochMilli()))
                                  .span("span-data-3".getBytes())
                                  .version(1L);
        StreamObserver<BanyandbTrace.WriteRequest> writeObserver
            = client.getTraceServiceStub().write(new StreamObserver<BanyandbTrace.WriteResponse>() {
            @Override
            public void onNext(BanyandbTrace.WriteResponse writeResponse) {
                assertEquals(BanyandbModel.Status.STATUS_SUCCEED.name(), writeResponse.getStatus());
            }

            @Override
            public void onError(Throwable throwable) {
                fail("write failed: " + throwable.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });
        try {
            writeObserver.onNext(trace1.build());
            writeObserver.onNext(trace2.build());
            writeObserver.onNext(trace3.build());
        } finally {
            writeObserver.onCompleted();
        }

        // Create trace query with order by start_time (no trace_id condition as it interferes with ordering)
        TraceQuery query = new TraceQuery(
            Lists.newArrayList(groupName),
            traceName,
            new TimestampRange(baseTime.toEpochMilli(), baseTime.plusSeconds(60).toEpochMilli()),
            ImmutableSet.of("start_time")
        );
        query.setOrderBy(new AbstractQuery.OrderBy("start_time", AbstractQuery.Sort.DESC));

        // Execute query and verify ordering
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TraceQueryResponse response = client.query(query);
            assertNotNull(response);
            assertFalse(response.isEmpty());
            assertEquals(2, response.size());

            // Verify we can access trace data
            assertNotNull(response.getTraces());
            assertEquals(2, response.getTraces().size());

            // Get spans from each trace and verify that span content matches expected data
            BanyandbTrace.Trace firstTrace = response.getTraces().get(0);
            BanyandbTrace.Trace secondTrace = response.getTraces().get(1);

            assertEquals(1, firstTrace.getSpansCount());
            assertEquals(1, secondTrace.getSpansCount());

            String firstSpanContent = new String(firstTrace.getSpans(0).getSpan().toByteArray());
            String secondSpanContent = new String(secondTrace.getSpans(0).getSpan().toByteArray());

            // Since we're ordering by start_time DESC, span-data-2 should come before span-data-1
            // (baseTime+60 > baseTime)
            assertEquals("span-data-2", firstSpanContent, "First span should be span-data-2 (newer timestamp)");
            assertEquals("span-data-1", secondSpanContent, "Second span should be span-data-1 (older timestamp)");
        });
    }

    private BanyandbDatabase.IndexRule buildIndexRule() {
        return BanyandbDatabase.IndexRule.newBuilder()
                                         .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                             .setGroup(groupName)
                                                                             .setName("start_time"))
                                         .addTags("start_time")
                                         .setType(BanyandbDatabase.IndexRule.Type.TYPE_TREE)
                                         .build();
    }

    private BanyandbDatabase.IndexRuleBinding buildIndexRuleBinding() {
        return BanyandbDatabase.IndexRuleBinding.newBuilder()
                                                .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                                    .setGroup(groupName)
                                                                                    .setName("trace_binding"))
                                                .setSubject(BanyandbDatabase.Subject.newBuilder()
                                                                                    .setCatalog(
                                                                                        BanyandbCommon.Catalog.CATALOG_TRACE)
                                                                                    .setName(traceName))
                                                .addAllRules(Arrays.asList("start_time"))
                                                .setBeginAt(TimeUtils.buildTimestamp(
                                                    ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
                                                .setExpireAt(TimeUtils.buildTimestamp(DEFAULT_EXPIRE_AT))
                                                .build();
    }
}