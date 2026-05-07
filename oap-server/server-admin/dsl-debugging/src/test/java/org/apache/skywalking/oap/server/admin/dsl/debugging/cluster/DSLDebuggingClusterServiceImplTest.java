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
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterRuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSessionLimits;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugSamplesAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugSamplesRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.DSLDebuggingClusterServiceGrpc;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallState;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link DSLDebuggingClusterServiceImpl} via
 * {@code InProcessServerBuilder} — keeps the gRPC transport real (deadlines,
 * status codes, serialisation) without TCP loopback. Verifies the four RPCs
 * walk the same {@link DebugSessionRegistry} paths the local POST does.
 */
public class DSLDebuggingClusterServiceImplTest {

    private static final RuleKey RULE = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");

    private Server server;
    private ManagedChannel channel;
    private DSLDebuggingClusterServiceGrpc.DSLDebuggingClusterServiceBlockingStub stub;
    private DebugSessionRegistry registry;
    private GateHolder holder;

    @BeforeEach
    public void setUp() throws IOException {
        registry = new DebugSessionRegistry();
        holder = new GateHolder("hash-1");
        registry.registerLookup(new DebugHolderLookup() {
            @Override
            public boolean serves(final RuleKey key) {
                return key.getCatalog() == Catalog.OTEL_RULES;
            }

            @Override
            public GateHolder lookup(final RuleKey key) {
                return RULE.equals(key) ? holder : null;
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
                return new AbstractDebugRecorder(sessionId, key, boundHolder, limits) {
                };
            }
        });

        final String name = "dsl-debug-test-" + System.nanoTime();
        server = InProcessServerBuilder.forName(name)
                                       .directExecutor()
                                       .addService(new DSLDebuggingClusterServiceImpl(registry, "node-A"))
                                       .build()
                                       .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = DSLDebuggingClusterServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    public void install_thenStop_walksLocalRegistry() {
        final InstallDebugSessionAck installAck = stub.installDebugSession(
            InstallDebugSessionRequest.newBuilder()
                .setSessionId("session-1")
                .setClientId("client-A")
                .setRuleKey(ruleKey(RULE))
                .setLimits(defaultLimits())
                .build());

        assertEquals(InstallState.INSTALLED, installAck.getState());
        assertEquals("node-A", installAck.getNodeId());
        assertTrue(holder.isGateOn(), "install must flip the holder gate");
        assertEquals(1, holder.getRecorders().length);

        final StopDebugSessionAck stopAck = stub.stopDebugSession(
            StopDebugSessionRequest.newBuilder()
                .setSessionId("session-1")
                .build());

        assertTrue(stopAck.getStopped());
        assertFalse(holder.isGateOn(), "stop must flip the gate back");
        assertEquals(0, holder.getRecorders().length);
    }

    @Test
    public void install_unknownRule_returnsNotLocal() {
        final RuleKey unknown = new RuleKey(Catalog.OTEL_RULES, "vm", "memory");
        final InstallDebugSessionAck ack = stub.installDebugSession(
            InstallDebugSessionRequest.newBuilder()
                .setSessionId("session-2")
                .setClientId("client-B")
                .setRuleKey(ruleKey(unknown))
                .setLimits(defaultLimits())
                .build());

        assertEquals(InstallState.NOT_LOCAL, ack.getState());
        assertFalse(holder.isGateOn(), "unknown-rule install must not touch the holder");
    }

    @Test
    public void install_duplicateSessionId_returnsAlreadyInstalled() {
        stub.installDebugSession(InstallDebugSessionRequest.newBuilder()
            .setSessionId("session-3").setRuleKey(ruleKey(RULE))
            .setLimits(defaultLimits()).build());

        final InstallDebugSessionAck duplicate = stub.installDebugSession(
            InstallDebugSessionRequest.newBuilder()
                .setSessionId("session-3").setRuleKey(ruleKey(RULE))
                .setLimits(defaultLimits()).build());

        assertEquals(InstallState.ALREADY_INSTALLED, duplicate.getState());
        assertEquals(1, holder.getRecorders().length, "duplicate must not double-bind");
    }

    @Test
    public void stopByClientId_terminatesEverySessionForThatClient() {
        stub.installDebugSession(InstallDebugSessionRequest.newBuilder()
            .setSessionId("session-A").setClientId("client-X").setRuleKey(ruleKey(RULE))
            .setLimits(defaultLimits()).build());

        final StopByClientIdAck ack = stub.stopByClientId(
            StopByClientIdRequest.newBuilder().setClientId("client-X").build());

        assertEquals(1, ack.getStoppedCount());
        assertTrue(ack.getStoppedSessionIdsList().contains("session-A"));
        assertFalse(holder.isGateOn());
    }

    @Test
    public void collect_returnsLocalSlice_orNotLocalForUnknown() {
        stub.installDebugSession(InstallDebugSessionRequest.newBuilder()
            .setSessionId("session-C").setRuleKey(ruleKey(RULE))
            .setLimits(defaultLimits()).build());

        final CollectDebugSamplesAck local = stub.collectDebugSamples(
            CollectDebugSamplesRequest.newBuilder().setSessionId("session-C").build());
        assertEquals("ok", local.getStatus());
        assertEquals(0, local.getRecordsCount(), "no records yet — no probe has fired in unit test");

        final CollectDebugSamplesAck unknown = stub.collectDebugSamples(
            CollectDebugSamplesRequest.newBuilder().setSessionId("never-installed").build());
        assertEquals("not_local", unknown.getStatus());
    }

    private static ClusterRuleKey ruleKey(final RuleKey key) {
        return ClusterRuleKey.newBuilder()
            .setCatalog(key.getCatalog().getWireName())
            .setName(key.getName())
            .setRuleName(key.getRuleName())
            .build();
    }

    private static ClusterSessionLimits defaultLimits() {
        return ClusterSessionLimits.newBuilder()
            .setRecordCap(SessionLimits.DEFAULT.getRecordCap())
            .setRetentionMillis(SessionLimits.DEFAULT.getRetentionMillis())
            .build();
    }
}
