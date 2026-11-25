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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterRuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSessionLimits;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsPart;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsSummary;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.DSLDebuggingClusterServiceGrpc;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallState;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CapturePage;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CaptureTime;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSession;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.ExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Granularity;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.InstallOutcome;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;

/**
 * Receiver-side implementation of the cluster RPCs. Sibling of
 * {@code RuntimeRuleClusterServiceImpl}; both register on the same
 * cluster gRPC server (the OAP cluster bus) so peers reach each other
 * through one transport.
 *
 * <h2>Workflow per RPC</h2>
 * <pre>
 *   InstallDebugSession  ─► registry.installWithId(...)       ─► INSTALLED | NOT_LOCAL | REJECTED
 *   CollectDebugRecords  ─► registry.find(sessionId).page     ─► record, ..., record, summary
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

    /**
     * The page one record a message, then its summary, so only a single record has to fit one message.
     */
    @Override
    public void collectDebugRecords(final CollectDebugRecordsRequest request,
                                    final StreamObserver<CollectDebugRecordsPart> responseObserver) {
        final CollectDebugRecordsSummary.Builder summary = CollectDebugRecordsSummary.newBuilder()
                                                                                     .setNodeId(selfNodeId)
                                                                                     .setInstanceId(
                                                                                         sessionRegistry.getInstanceId());
        for (final ClusterExecutionRecord record : page(request, summary)) {
            responseObserver.onNext(CollectDebugRecordsPart.newBuilder().setRecord(record).build());
        }
        responseObserver.onNext(CollectDebugRecordsPart.newBuilder().setSummary(summary).build());
        responseObserver.onCompleted();
    }

    /**
     * @return the records the request's page holds, its summary set on {@code summary}
     */
    private List<ClusterExecutionRecord> page(final CollectDebugRecordsRequest request,
                                              final CollectDebugRecordsSummary.Builder summary) {
        final DebugSession session = sessionRegistry.find(request.getSessionId());
        if (session == null) {
            summary.setStatus("not_local");
            return Collections.emptyList();
        }
        // The page is measured in the bytes the messages carry, converting each record once.
        final Map<ExecutionRecord, ClusterExecutionRecord> converted = new IdentityHashMap<>();
        final CapturePage page = session.getRecorder().page(
            request.hasCapturedSince() ? CaptureTimeCodec.decode(request.getCapturedSince()) : CaptureTime.EARLIEST,
            Math.max(1, request.getMaxRecords()), request.getMaxBytes(),
            r -> converted.computeIfAbsent(r, DSLDebuggingClusterServiceImpl::toClusterRecord).getSerializedSize());
        final List<ClusterExecutionRecord> records = new ArrayList<>(page.getRecords().size());
        for (final ExecutionRecord r : page.getRecords()) {
            records.add(fitMessage(converted.computeIfAbsent(r, DSLDebuggingClusterServiceImpl::toClusterRecord),
                request.getMaxRecordBytes()));
        }
        summary.setStatus(page.isCaptured() ? "captured" : "ok")
               .setCaptured(page.isCaptured())
               .setTotalBytes(page.getTotalBytes())
               .setCompleteBefore(CaptureTimeCodec.encode(page.getCompleteBefore()));
        return records;
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

    /**
     * A record larger than the caller's channel takes in one message goes as a stub holding its size, so the read,
     * and with it the page, still moves on; read on its own node, it is whole.
     *
     * @param maxRecordBytes the largest record the caller takes; 0 takes every record whole
     */
    static ClusterExecutionRecord fitMessage(final ClusterExecutionRecord record, final long maxRecordBytes) {
        final int size = record.getSerializedSize();
        if (maxRecordBytes <= 0 || size <= maxRecordBytes) {
            return record;
        }
        return ClusterExecutionRecord.newBuilder()
                                     .setStartedAtMs(record.getStartedAtMs())
                                     .setCapturedAt(record.getCapturedAt())
                                     .putAllMetadata(record.getMetadataMap())
                                     .setOversizedBytes(size)
                                     .build();
    }

    private static ClusterExecutionRecord toClusterRecord(final ExecutionRecord r) {
        final ClusterExecutionRecord.Builder builder = ClusterExecutionRecord.newBuilder()
            .setStartedAtMs(r.getStartedAtMillis())
            .setCapturedAt(CaptureTimeCodec.encode(r.getCapturedAt()))
            .setDsl(r.getDsl() == null ? "" : r.getDsl());
        if (r.getMetadata() != null && !r.getMetadata().isEmpty()) {
            builder.putAllMetadata(r.getMetadata());
        }
        for (final Sample s : r.getSamples()) {
            builder.addSamples(toClusterSample(s));
        }
        if (r.getBodyJson() != null) {
            builder.setBodyJson(r.getBodyJson());
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
            .setReason(s.getReason() == null ? "" : s.getReason())
            .build();
    }
}
