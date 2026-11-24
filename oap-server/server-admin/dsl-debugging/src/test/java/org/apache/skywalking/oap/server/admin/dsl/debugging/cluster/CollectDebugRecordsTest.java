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

package org.apache.skywalking.oap.server.admin.dsl.debugging.cluster;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterRuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSessionLimits;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsPart;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsSummary;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.DSLDebuggingClusterServiceGrpc;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectDebugRecordsTest {

    private static final RuleKey RULE = new RuleKey(Catalog.TAL, "otlp-general", "otlp-general");

    private Server server;
    private ManagedChannel channel;
    private DSLDebuggingClusterServiceGrpc.DSLDebuggingClusterServiceBlockingStub stub;
    private TestStreamedRecorder recorder;

    @BeforeEach
    public void setUp() throws IOException {
        final DebugSessionRegistry registry = new DebugSessionRegistry();
        final GateHolder holder = new GateHolder("filter {}");
        registry.registerLookup(new DebugHolderLookup() {
            @Override
            public boolean serves(final RuleKey key) {
                return true;
            }

            @Override
            public GateHolder lookup(final RuleKey key) {
                return holder;
            }
        });
        registry.registerRecorderFactory(new DebugRecorderFactory() {
            @Override
            public boolean serves(final RuleKey key) {
                return true;
            }

            @Override
            public AbstractDebugRecorder create(final String sessionId, final RuleKey key,
                                                final GateHolder boundHolder, final SessionLimits limits) {
                recorder = new TestStreamedRecorder(sessionId, key, boundHolder, limits);
                return recorder;
            }
        });
        final String name = "dsl-debug-stream-test-" + System.nanoTime();
        server = InProcessServerBuilder.forName(name).directExecutor()
                                       .addService(new DSLDebuggingClusterServiceImpl(registry, "node-B"))
                                       .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = DSLDebuggingClusterServiceGrpc.newBlockingStub(channel);
        stub.installDebugSession(InstallDebugSessionRequest.newBuilder()
            .setSessionId("session-S")
            .setRuleKey(ClusterRuleKey.newBuilder().setCatalog(RULE.getCatalog().getWireName())
                                      .setName(RULE.getName()).setRuleName(RULE.getRuleName()))
            .setLimits(ClusterSessionLimits.newBuilder().setRecordCap(100).setRetentionMillis(60_000L))
            .build());
    }

    @AfterEach
    public void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void thePageStreamsOneRecordAMessageThenItsSummary() {
        recorder.publish("{\"n\":1}");
        recorder.publish("{\"big\":\"" + "x".repeat(2000) + "\"}");
        recorder.publish("{\"n\":3}");

        final List<CollectDebugRecordsPart> parts = new ArrayList<>();
        stub.collectDebugRecords(request(1000)).forEachRemaining(parts::add);

        assertEquals(4, parts.size(), "three records, then the summary");
        assertTrue(parts.get(0).hasRecord() && parts.get(1).hasRecord() && parts.get(2).hasRecord());
        final ClusterExecutionRecord big = parts.get(1).getRecord();
        assertTrue(big.getOversizedBytes() > 1000, "a record larger than one message takes goes as its size");
        assertTrue(big.getBodyJson().isEmpty());
        assertEquals("{\"n\":3}", parts.get(2).getRecord().getBodyJson());
        final CollectDebugRecordsSummary summary = parts.get(3).getSummary();
        assertEquals("ok", summary.getStatus());
        assertTrue(CaptureTimeCodec.decode(parts.get(2).getRecord().getCapturedAt())
                                   .compareTo(CaptureTimeCodec.decode(summary.getCompleteBefore())) <= 0);
    }

    @Test
    void theServingNodeReadsTheStreamAsOneReply() {
        recorder.publish("{\"n\":1}");
        recorder.publish("{\"n\":2}");

        final PeerPage page = DSLDebuggingClusterClient.assemble(stub.collectDebugRecords(request(0)));

        assertEquals(2, page.getRecords().size());
        assertEquals("{\"n\":1}", page.getRecords().get(0).getBodyJson());
        assertEquals("node-B", page.getSummary().getNodeId());
        assertEquals("ok", page.getSummary().getStatus());
    }

    @Test
    void aStreamEndingWithoutItsSummaryFails() {
        final Iterator<CollectDebugRecordsPart> recordsOnly = Collections.singletonList(
            CollectDebugRecordsPart.newBuilder().setRecord(ClusterExecutionRecord.getDefaultInstance()).build())
                                                                           .iterator();
        assertThrows(IllegalStateException.class, () -> DSLDebuggingClusterClient.assemble(recordsOnly));
    }

    @Test
    void aStreamGoingOnAfterItsSummaryFails() {
        final Iterator<CollectDebugRecordsPart> pastTheSummary = Arrays.asList(
            CollectDebugRecordsPart.newBuilder().setSummary(CollectDebugRecordsSummary.getDefaultInstance()).build(),
            CollectDebugRecordsPart.newBuilder().setRecord(ClusterExecutionRecord.getDefaultInstance()).build())
                                                                         .iterator();
        assertThrows(IllegalStateException.class, () -> DSLDebuggingClusterClient.assemble(pastTheSummary));
    }

    private static CollectDebugRecordsRequest request(final long maxRecordBytes) {
        return CollectDebugRecordsRequest.newBuilder()
                                         .setSessionId("session-S")
                                         .setMaxRecords(100)
                                         .setMaxBytes(Long.MAX_VALUE)
                                         .setMaxRecordBytes(maxRecordBytes)
                                         .build();
    }

    private static final class TestStreamedRecorder extends AbstractDebugRecorder {
        TestStreamedRecorder(final String sessionId, final RuleKey key, final GateHolder holder,
                             final SessionLimits limits) {
            super(sessionId, key, holder, limits);
        }

        void publish(final String body) {
            publishRecord(newRecord(System.currentTimeMillis(), body));
        }
    }
}
