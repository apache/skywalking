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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterRuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSessionLimits;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsPart;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsSummary;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.DSLDebuggingClusterServiceGrpc;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionRequest;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CaptureTime;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;

/**
 * Outbound cluster broadcasts for the four debug-session RPCs.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   POST /dsl-debugging/session
 *     │
 *     ├─ stopByClientId(clientId)         broadcast cleanup of any prior session under
 *     │                                    this clientId on every peer (per SWIP §6).
 *     ├─ install(sessionId, ruleKey, ...) broadcast install to every peer; each peer
 *     │                                    binds a local recorder to its own holder.
 *     ▼
 *
 *   GET /dsl-debugging/session/{id}
 *     └─ collect(sessionId, point, page)  each peer streams the records it captured at or after
 *                                          the point, then a summary; the receiving node keeps every
 *                                          node's records before the point all are complete before.
 *
 *   POST /dsl-debugging/session/{id}/stop
 *     └─ stop(sessionId)                   best-effort broadcast; missed peers fall out via
 *                                          retention timeout.
 * </pre>
 *
 * <p>Each broadcast returns one {@link PeerOutcome} per non-self peer; the
 * receiving node's REST handler aggregates them under the SWIP §1 response
 * shape.
 */
@RequiredArgsConstructor
public final class DSLDebuggingClusterClient {

    private final ClusterPeerCaller peerCaller;
    private final long perCallDeadlineMs;
    private final long collectDeadlineMs;
    /**
     * The largest record this node's peer channels take in one message; a peer sends a larger one as a stub.
     */
    private final long maxRecordBytes;

    public List<PeerOutcome<InstallDebugSessionAck>> broadcastInstall(
            final String sessionId, final String clientId, final RuleKey ruleKey,
            final SessionLimits limits) {
        final InstallDebugSessionRequest req = InstallDebugSessionRequest.newBuilder()
            .setSessionId(sessionId)
            .setClientId(clientId == null ? "" : clientId)
            .setRuleKey(toClusterRuleKey(ruleKey))
            .setLimits(toClusterLimits(limits))
            .setSenderNodeId(peerCaller.getSelfNodeId())
            .setIssuedAtMs(System.currentTimeMillis())
            .build();
        return peerCaller.fanOut("InstallDebugSession", channel ->
            DSLDebuggingClusterServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(perCallDeadlineMs, TimeUnit.MILLISECONDS)
                .installDebugSession(req));
    }

    /**
     * @param responseBytes the bytes one response holds, from every node
     * @return the bytes each node's page may hold: the response's shared by every node
     */
    public long nodePageBytes(final long responseBytes) {
        return responseBytes / peerCaller.nodeCount();
    }

    /**
     * Read a page of the session from every other node.
     *
     * @param capturedSince the point the client read from; {@link CaptureTime#EARLIEST} for the first page
     * @param maxRecords    at most this many records from each node
     * @param maxBytes      at most this many bytes from each node
     */
    public List<PeerOutcome<PeerPage>> broadcastCollect(final String sessionId,
                                                        final CaptureTime capturedSince,
                                                        final int maxRecords,
                                                        final long maxBytes) {
        final CollectDebugRecordsRequest.Builder builder = CollectDebugRecordsRequest.newBuilder()
            .setSessionId(sessionId)
            .setSenderNodeId(peerCaller.getSelfNodeId())
            .setMaxRecords(maxRecords)
            .setMaxBytes(maxBytes)
            .setMaxRecordBytes(maxRecordBytes);
        if (!CaptureTime.EARLIEST.equals(capturedSince)) {
            builder.setCapturedSince(CaptureTimeCodec.encode(capturedSince));
        }
        final CollectDebugRecordsRequest req = builder.build();
        return peerCaller.fanOut("CollectDebugRecords", collectDeadlineMs, channel -> assemble(
            DSLDebuggingClusterServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(collectDeadlineMs, TimeUnit.MILLISECONDS)
                .collectDebugRecords(req)));
    }

    /**
     * @return the peer's page: its records, in the order they came, and the summary that ended them
     */
    static PeerPage assemble(final Iterator<CollectDebugRecordsPart> parts) {
        final List<ClusterExecutionRecord> records = new ArrayList<>();
        CollectDebugRecordsSummary summary = null;
        while (parts.hasNext()) {
            final CollectDebugRecordsPart part = parts.next();
            if (summary != null) {
                throw new IllegalStateException("the peer sent more after the summary that ends its page");
            }
            if (part.hasRecord()) {
                records.add(part.getRecord());
            } else if (part.hasSummary()) {
                summary = part.getSummary();
            }
        }
        if (summary == null) {
            throw new IllegalStateException("the peer's records ended without their summary");
        }
        return new PeerPage(summary, records);
    }

    public List<PeerOutcome<StopDebugSessionAck>> broadcastStop(final String sessionId) {
        final StopDebugSessionRequest req = StopDebugSessionRequest.newBuilder()
            .setSessionId(sessionId)
            .setSenderNodeId(peerCaller.getSelfNodeId())
            .build();
        return peerCaller.fanOut("StopDebugSession", channel ->
            DSLDebuggingClusterServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(perCallDeadlineMs, TimeUnit.MILLISECONDS)
                .stopDebugSession(req));
    }

    public List<PeerOutcome<StopByClientIdAck>> broadcastStopByClientId(final String clientId) {
        final StopByClientIdRequest req = StopByClientIdRequest.newBuilder()
            .setClientId(clientId == null ? "" : clientId)
            .setSenderNodeId(peerCaller.getSelfNodeId())
            .build();
        return peerCaller.fanOut("StopByClientId", channel ->
            DSLDebuggingClusterServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(perCallDeadlineMs, TimeUnit.MILLISECONDS)
                .stopByClientId(req));
    }

    private static ClusterRuleKey toClusterRuleKey(final RuleKey ruleKey) {
        return ClusterRuleKey.newBuilder()
            .setCatalog(ruleKey.getCatalog().getWireName())
            .setName(ruleKey.getName())
            .setRuleName(ruleKey.getRuleName())
            .build();
    }

    private static ClusterSessionLimits toClusterLimits(final SessionLimits limits) {
        return ClusterSessionLimits.newBuilder()
            .setRecordCap(limits.getRecordCap())
            .setRetentionMillis(limits.getRetentionMillis())
            .setGranularity(limits.getGranularity().wireName())
            .build();
    }
}
