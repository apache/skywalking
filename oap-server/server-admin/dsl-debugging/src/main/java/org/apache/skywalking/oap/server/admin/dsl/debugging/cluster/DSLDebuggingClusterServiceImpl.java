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

import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterRuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSample;
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
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSession;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.ExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Granularity;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.InstallOutcome;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Receiver-side implementation of the cluster RPCs. Sibling of
 * {@code RuntimeRuleClusterServiceImpl}; both register on the same
 * cluster gRPC server (the OAP cluster bus) so peers reach each other
 * through one transport.
 *
 * <h2>Workflow per RPC</h2>
 * <pre>
 *   InstallDebugSession  ─► registry.installWithId(...)       ─► INSTALLED | NOT_LOCAL | REJECTED
 *   CollectDebugSamples  ─► registry.find(sessionId).snapshot ─► records[] + captured + bytes
 *   StopDebugSession     ─► registry.uninstall(sessionId)     ─► stopped: true|false
 *   StopByClientId       ─► registry.stopByClientId(clientId) ─► count + ids[]
 * </pre>
 *
 * <p>Every handler is a thin adapter over the local {@link DebugSessionRegistry}
 * so the local POST and the cluster RPC walk the same install/uninstall paths.
 * No special-cased peer logic — once a peer binds a recorder, the local
 * receiver-thread probes fire just like a local-installed session.
 */
@Slf4j
public final class DSLDebuggingClusterServiceImpl
        extends DSLDebuggingClusterServiceGrpc.DSLDebuggingClusterServiceImplBase {

    private final DebugSessionRegistry sessionRegistry;
    private final String selfNodeId;

    public DSLDebuggingClusterServiceImpl(final DebugSessionRegistry sessionRegistry,
                                          final String selfNodeId) {
        this.sessionRegistry = sessionRegistry;
        this.selfNodeId = selfNodeId == null ? "" : selfNodeId;
    }

    @Override
    public void installDebugSession(final InstallDebugSessionRequest request,
                                    final StreamObserver<InstallDebugSessionAck> responseObserver) {
        final InstallDebugSessionAck.Builder ack = InstallDebugSessionAck.newBuilder()
            .setNodeId(selfNodeId);
        try {
            final RuleKey ruleKey = toRuleKey(request.getRuleKey());
            final SessionLimits limits = toLimits(request.getLimits());
            final InstallOutcome outcome = sessionRegistry.installWithId(
                request.getSessionId(), ruleKey, request.getClientId(), limits);
            switch (outcome.getStatus()) {
                case INSTALLED:
                    ack.setState(InstallState.INSTALLED);
                    break;
                case ALREADY_INSTALLED:
                    ack.setState(InstallState.ALREADY_INSTALLED);
                    break;
                case TOO_MANY_SESSIONS:
                    ack.setState(InstallState.TOO_MANY_SESSIONS)
                       .setDetail("active-session ceiling reached on this peer");
                    break;
                case NOT_LOCAL:
                default:
                    ack.setState(InstallState.NOT_LOCAL)
                       .setDetail("no live holder for " + ruleKey + " on this node");
                    break;
            }
        } catch (final IllegalStateException ise) {
            ack.setState(InstallState.REJECTED).setDetail(ise.getMessage());
        } catch (final Throwable t) {
            log.warn("DSL debug InstallDebugSession failed for sessionId={}",
                     request.getSessionId(), t);
            ack.setState(InstallState.REJECTED).setDetail(t.getClass().getSimpleName());
        }
        responseObserver.onNext(ack.build());
        responseObserver.onCompleted();
    }

    @Override
    public void collectDebugSamples(final CollectDebugSamplesRequest request,
                                    final StreamObserver<CollectDebugSamplesAck> responseObserver) {
        final CollectDebugSamplesAck.Builder ack = CollectDebugSamplesAck.newBuilder()
            .setNodeId(selfNodeId);
        final DebugSession session = sessionRegistry.find(request.getSessionId());
        if (session == null) {
            ack.setStatus("not_local");
        } else {
            ack.setStatus(session.getRecorder().isCaptured() ? "captured" : "ok");
            ack.setCaptured(session.getRecorder().isCaptured());
            ack.setTotalBytes(session.getRecorder().totalBytes());
            for (final ExecutionRecord r : session.getRecorder().snapshotRecords()) {
                ack.addRecords(toClusterRecord(r));
            }
        }
        responseObserver.onNext(ack.build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopDebugSession(final StopDebugSessionRequest request,
                                 final StreamObserver<StopDebugSessionAck> responseObserver) {
        final boolean stopped = sessionRegistry.uninstall(request.getSessionId());
        responseObserver.onNext(StopDebugSessionAck.newBuilder()
            .setNodeId(selfNodeId).setStopped(stopped).build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopByClientId(final StopByClientIdRequest request,
                               final StreamObserver<StopByClientIdAck> responseObserver) {
        final List<String> ids = sessionRegistry.stopByClientId(request.getClientId());
        responseObserver.onNext(StopByClientIdAck.newBuilder()
            .setNodeId(selfNodeId)
            .setStoppedCount(ids.size())
            .addAllStoppedSessionIds(ids)
            .build());
        responseObserver.onCompleted();
    }

    private static RuleKey toRuleKey(final ClusterRuleKey wire) {
        return new RuleKey(Catalog.of(wire.getCatalog()), wire.getName(), wire.getRuleName());
    }

    private static SessionLimits toLimits(final ClusterSessionLimits wire) {
        if (wire == null) {
            return SessionLimits.DEFAULT;
        }
        // Defensive: peer might send 0 for unset fields. Fall back to defaults for any
        // field <= 0 so a partial limits payload doesn't pin captures to "no records".
        final int recordCap = wire.getRecordCap() > 0
            ? wire.getRecordCap() : SessionLimits.DEFAULT.getRecordCap();
        final long retention = wire.getRetentionMillis() > 0
            ? wire.getRetentionMillis() : SessionLimits.DEFAULT.getRetentionMillis();
        // Older peers omit granularity entirely → empty string → BLOCK default.
        final Granularity granularity = Granularity.ofWireName(wire.getGranularity());
        return new SessionLimits(recordCap, retention, granularity);
    }

    private static ClusterExecutionRecord toClusterRecord(final ExecutionRecord r) {
        final ClusterExecutionRecord.Builder builder = ClusterExecutionRecord.newBuilder()
            .setStartedAtMs(r.getStartedAtMillis())
            .setDsl(r.getDsl() == null ? "" : r.getDsl());
        if (r.getMetadata() != null && !r.getMetadata().isEmpty()) {
            builder.putAllMetadata(r.getMetadata());
        }
        for (final Sample s : r.getSamples()) {
            builder.addSamples(toClusterSample(s));
        }
        return builder.build();
    }

    private static ClusterSample toClusterSample(final Sample s) {
        return ClusterSample.newBuilder()
            .setType(s.getType() == null ? "" : s.getType())
            .setSourceText(s.getSourceText() == null ? "" : s.getSourceText())
            .setContinueOn(s.isContinueOn())
            .setPayloadJson(s.getPayloadJson() == null ? "{}" : s.getPayloadJson())
            .setSourceLine(s.getSourceLine())
            .build();
    }
}
