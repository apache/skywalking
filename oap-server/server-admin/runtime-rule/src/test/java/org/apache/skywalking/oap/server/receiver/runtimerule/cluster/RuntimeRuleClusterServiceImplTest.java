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

package org.apache.skywalking.oap.server.receiver.runtimerule.cluster;

import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ApplyStatusPhase;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ApplyStatusRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ApplyStatusResponse;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.NotifyAppliedAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.NotifyAppliedRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.status.ApplyPhase;
import org.apache.skywalking.oap.server.receiver.runtimerule.status.SchemaApplyCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for the admin-internal gRPC handlers the cluster main serves:
 * {@code getApplyStatus} (the apply-status query, including the {@link ApplyPhase} → proto mapping
 * and the laggard list) and {@code notifyApplied} (self-suppression + the off-thread reconcile
 * nudge). Status flows through the {@link SchemaApplyCoordinator#INSTANCE} singleton, so each test
 * opens its own apply (a fresh UUID applyId) to stay isolated from the others.
 */
class RuntimeRuleClusterServiceImplTest {

    private static final String SELF = "self-node_17129";

    /** Minimal StreamObserver that captures the single onNext value the handlers emit. */
    private static final class Capturing<T> implements StreamObserver<T> {
        private T value;

        @Override
        public void onNext(final T v) {
            this.value = v;
        }

        @Override
        public void onError(final Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    }

    private ApplyStatusResponse query(final RuntimeRuleClusterServiceImpl impl, final String applyId) {
        final Capturing<ApplyStatusResponse> obs = new Capturing<>();
        impl.getApplyStatus(ApplyStatusRequest.newBuilder().setApplyId(applyId).build(), obs);
        return obs.value;
    }

    @Test
    void getApplyStatusMapsEachPhaseToProtoAlongTheHappyPath() {
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(mock(DSLManager.class), SELF);
        final String id = SchemaApplyCoordinator.INSTANCE.begin("otel-rules", "vm-phase", "h1");

        assertEquals(ApplyStatusPhase.APPLY_PHASE_PENDING, query(impl, id).getPhase());
        SchemaApplyCoordinator.INSTANCE.transition(id, ApplyPhase.DDL);
        assertEquals(ApplyStatusPhase.APPLY_PHASE_DDL, query(impl, id).getPhase());
        SchemaApplyCoordinator.INSTANCE.markFencing(id);
        assertEquals(ApplyStatusPhase.APPLY_PHASE_FENCING, query(impl, id).getPhase());
        SchemaApplyCoordinator.INSTANCE.transition(id, ApplyPhase.ROLLING_OUT);
        assertEquals(ApplyStatusPhase.APPLY_PHASE_ROLLING_OUT, query(impl, id).getPhase());
        SchemaApplyCoordinator.INSTANCE.markApplied(id);

        final ApplyStatusResponse applied = query(impl, id);
        assertTrue(applied.getFound());
        assertEquals(ApplyStatusPhase.APPLY_PHASE_APPLIED, applied.getPhase());
        assertEquals("otel-rules", applied.getCatalog());
        assertEquals("vm-phase", applied.getName());
        assertEquals(SELF, applied.getNodeId());
        assertTrue(applied.getFenceLaggardsList().isEmpty());
    }

    @Test
    void getApplyStatusSurfacesDegradedWithLaggardNodeIds() {
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(mock(DSLManager.class), SELF);
        final String id = SchemaApplyCoordinator.INSTANCE.begin("otel-rules", "vm-degraded", "h2");
        SchemaApplyCoordinator.INSTANCE.markDegraded(id, "fence did not confirm",
            Arrays.asList("data-1_17912", "data-2_17912"));

        final ApplyStatusResponse resp = query(impl, id);
        assertEquals(ApplyStatusPhase.APPLY_PHASE_DEGRADED, resp.getPhase());
        assertEquals("fence did not confirm", resp.getFailureReason());
        assertEquals(Arrays.asList("data-1_17912", "data-2_17912"), resp.getFenceLaggardsList());
    }

    @Test
    void getApplyStatusMapsFailed() {
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(mock(DSLManager.class), SELF);
        final String id = SchemaApplyCoordinator.INSTANCE.begin("otel-rules", "vm-failed", "h3");
        SchemaApplyCoordinator.INSTANCE.markFailed(id, "ddl_verify_failed");

        final ApplyStatusResponse resp = query(impl, id);
        assertEquals(ApplyStatusPhase.APPLY_PHASE_FAILED, resp.getPhase());
        assertEquals("ddl_verify_failed", resp.getFailureReason());
        assertTrue(resp.getFenceLaggardsList().isEmpty());
    }

    @Test
    void getApplyStatusReturnsUnknownWhenNothingTracked() {
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(mock(DSLManager.class), SELF);

        final ApplyStatusResponse resp = query(impl, "no-such-apply-id");
        assertFalse(resp.getFound());
        assertEquals(ApplyStatusPhase.APPLY_PHASE_UNKNOWN, resp.getPhase());
        // node_id is still stamped so the caller knows which node answered.
        assertEquals(SELF, resp.getNodeId());
    }

    @Test
    void getApplyStatusResolvesByCatalogNameWhenApplyIdAbsent() {
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(mock(DSLManager.class), SELF);
        final String id = SchemaApplyCoordinator.INSTANCE.begin("otel-rules", "vm-byname", "hbyname");
        SchemaApplyCoordinator.INSTANCE.markApplied(id);

        final Capturing<ApplyStatusResponse> obs = new Capturing<>();
        impl.getApplyStatus(ApplyStatusRequest.newBuilder()
            .setCatalog("otel-rules").setName("vm-byname").setContentHash("hbyname").build(), obs);

        assertTrue(obs.value.getFound());
        assertEquals(ApplyStatusPhase.APPLY_PHASE_APPLIED, obs.value.getPhase());
        assertEquals(id, obs.value.getApplyId());
    }

    @Test
    void notifyAppliedSuppressesSelfBroadcastAndDoesNotReconcile() {
        final DSLManager dslManager = mock(DSLManager.class);
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(dslManager, SELF);
        final Capturing<NotifyAppliedAck> obs = new Capturing<>();

        impl.notifyApplied(NotifyAppliedRequest.newBuilder()
            .setSenderNodeId(SELF).setCatalog("otel-rules").setName("vm").build(), obs);

        assertFalse(obs.value.getAccepted(), "a node's own broadcast must be suppressed");
        verify(dslManager, never()).tick();
    }

    @Test
    void notifyAppliedFromPeerSchedulesAReconcileTick() throws Exception {
        final DSLManager dslManager = mock(DSLManager.class);
        final CountDownLatch ticked = new CountDownLatch(1);
        doAnswer(inv -> {
            ticked.countDown();
            return null;
        }).when(dslManager).tick();
        final RuntimeRuleClusterServiceImpl impl = new RuntimeRuleClusterServiceImpl(dslManager, SELF);
        final Capturing<NotifyAppliedAck> obs = new Capturing<>();

        impl.notifyApplied(NotifyAppliedRequest.newBuilder()
            .setSenderNodeId("other-node_17129").setCatalog("otel-rules").setName("vm").build(), obs);

        assertTrue(obs.value.getAccepted(), "a peer notify must be accepted");
        assertTrue(ticked.await(3, TimeUnit.SECONDS),
            "the reconcile tick must run off the gRPC thread");
    }
}
