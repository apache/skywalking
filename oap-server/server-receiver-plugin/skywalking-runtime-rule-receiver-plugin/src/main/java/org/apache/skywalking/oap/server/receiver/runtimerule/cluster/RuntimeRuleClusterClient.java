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

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ForwardRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ForwardResponse;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ResumeAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ResumeRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.RuntimeRuleClusterServiceGrpc;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendRequest;

/**
 * Client-side broadcast of the Suspend and Resume RPCs during a STRUCTURAL apply. Reuses the
 * established inter-node {@link ManagedChannel}s owned by {@link RemoteClientManager} — no
 * duplicate channel caching, no duplicate TLS config. Peer discovery is delegated to the
 * cluster module via {@code RemoteClientManager#getRemoteClient()}; self is filtered out by
 * address.
 *
 * <p>Sequential fan-out with a per-call deadline. Unreachable peers are logged and skipped —
 * the main node does not abort on a single peer failure. For Suspend, unreachable peers
 * recover via the dslManager's self-heal sweep when the DB content eventually changes or the
 * self-heal threshold elapses. For Resume, unreachable peers remain SUSPENDED until the
 * self-heal threshold elapses or the DB changes on the next main-node retry.
 */
@Slf4j
public final class RuntimeRuleClusterClient {

    private final RemoteClientManager remoteClientManager;
    private final String selfNodeId;
    private final long perCallDeadlineMs;

    public RuntimeRuleClusterClient(final RemoteClientManager remoteClientManager,
                                    final String selfNodeId,
                                    final long perCallDeadlineMs) {
        this.remoteClientManager = remoteClientManager;
        this.selfNodeId = selfNodeId;
        this.perCallDeadlineMs = perCallDeadlineMs;
    }

    /**
     * Fan out Suspend to every non-self peer sequentially. Sequential rather than parallel
     * because (a) peer count is typically small (2–10 in practice), (b) the blocking stubs
     * already carry their own deadlines so worst-case fan-out time is bounded to
     * {@code peers * perCallDeadlineMs}, (c) sequential matches the existing cluster-bus code
     * style and avoids introducing yet another executor for a short-lived operation.
     *
     * @return aggregated ack list in iteration order. Entries for unreachable peers are null.
     *         Main node workflow proceeds regardless.
     */
    public List<SuspendAck> broadcastSuspend(final String catalog, final String name, final String reason) {
        final List<RemoteClient> peers = remoteClientManager.getRemoteClient();
        final List<SuspendAck> acks = new ArrayList<>(peers.size());
        for (final RemoteClient peer : peers) {
            if (peer.getAddress() != null && peer.getAddress().isSelf()) {
                continue;
            }
            acks.add(suspendOne(peer, catalog, name, reason));
        }
        return acks;
    }

    private SuspendAck suspendOne(final RemoteClient peer, final String catalog,
                                  final String name, final String reason) {
        final ManagedChannel channel = peer.getChannel();
        if (channel == null) {
            log.warn("runtime-rule Suspend skipped for peer {}: channel not yet established",
                peer.getAddress());
            return null;
        }
        final RuntimeRuleClusterServiceGrpc.RuntimeRuleClusterServiceBlockingStub stub =
            RuntimeRuleClusterServiceGrpc.newBlockingStub(channel)
                                         .withDeadlineAfter(perCallDeadlineMs, TimeUnit.MILLISECONDS);
        try {
            return stub.suspend(SuspendRequest.newBuilder()
                .setCatalog(catalog)
                .setName(name)
                .setReason(reason == null ? "" : reason)
                .setSenderNodeId(selfNodeId)
                .setIssuedAtMs(System.currentTimeMillis())
                .build());
        } catch (final Throwable t) {
            log.warn("runtime-rule Suspend to peer {} failed for {}/{}: {}",
                peer.getAddress(), catalog, name, t.getMessage());
            return null;
        }
    }

    /**
     * Fan out Resume to every non-self peer. Same transport, same sequential-with-deadline
     * policy as {@link #broadcastSuspend}. Called by the REST handler's failure branches so
     * peers flip back to RUNNING within an RPC round-trip instead of waiting for the 60 s
     * self-heal threshold in the 99% case. Unreachable peers fall through to self-heal.
     */
    public List<ResumeAck> broadcastResume(final String catalog, final String name,
                                            final String reason) {
        final List<RemoteClient> peers = remoteClientManager.getRemoteClient();
        final List<ResumeAck> acks = new ArrayList<>(peers.size());
        for (final RemoteClient peer : peers) {
            if (peer.getAddress() != null && peer.getAddress().isSelf()) {
                continue;
            }
            acks.add(resumeOne(peer, catalog, name, reason));
        }
        return acks;
    }

    private ResumeAck resumeOne(final RemoteClient peer, final String catalog,
                                 final String name, final String reason) {
        final ManagedChannel channel = peer.getChannel();
        if (channel == null) {
            log.warn("runtime-rule Resume skipped for peer {}: channel not yet established",
                peer.getAddress());
            return null;
        }
        final RuntimeRuleClusterServiceGrpc.RuntimeRuleClusterServiceBlockingStub stub =
            RuntimeRuleClusterServiceGrpc.newBlockingStub(channel)
                                         .withDeadlineAfter(perCallDeadlineMs, TimeUnit.MILLISECONDS);
        try {
            return stub.resume(ResumeRequest.newBuilder()
                .setCatalog(catalog)
                .setName(name)
                .setReason(reason == null ? "" : reason)
                .setSenderNodeId(selfNodeId)
                .setIssuedAtMs(System.currentTimeMillis())
                .build());
        } catch (final Throwable t) {
            log.warn("runtime-rule Resume to peer {} failed for {}/{}: {}",
                peer.getAddress(), catalog, name, t.getMessage());
            return null;
        }
    }

    /**
     * Forward a write request to the main node for {@code (catalog, name)}. The caller has
     * already computed the main via {@link MainRouter#mainClient}. Locates the
     * {@link RemoteClient} whose address matches {@code mainAddr} and issues the RPC.
     *
     * <p>Uses a longer deadline than Suspend / Resume because the forwarded workflow on the
     * main can include compile + DDL + persist which is orders of magnitude slower than a
     * bookkeeping broadcast. Caller supplies the deadline in ms so admin operations can
     * tune it independently of cluster-control fan-outs.
     *
     * @return the main's response. Never null on success; throws on transport failure so
     *         the caller can surface a clear diagnostic to the operator.
     */
    public ForwardResponse forwardToMain(final Address mainAddr,
                                          final String operation,
                                          final String catalog, final String name,
                                          final byte[] body,
                                          final boolean allowStorageChange,
                                          final boolean forceReapply,
                                          final long deadlineMs) {
        final ManagedChannel channel = findChannelForAddress(mainAddr);
        if (channel == null) {
            throw new IllegalStateException(
                "no cluster channel to forward-target " + mainAddr + " (peer list out of sync?)");
        }
        final RuntimeRuleClusterServiceGrpc.RuntimeRuleClusterServiceBlockingStub stub =
            RuntimeRuleClusterServiceGrpc.newBlockingStub(channel)
                                         .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
        return stub.forward(ForwardRequest.newBuilder()
            .setOperation(operation == null ? "" : operation)
            .setCatalog(catalog == null ? "" : catalog)
            .setName(name == null ? "" : name)
            .setBody(body == null ? ByteString.EMPTY : ByteString.copyFrom(body))
            .setAllowStorageChange(allowStorageChange)
            .setForceReapply(forceReapply)
            .setSenderNodeId(selfNodeId)
            .setIssuedAtMs(System.currentTimeMillis())
            .build());
    }

    /**
     * Walk the active peer list and return the channel whose address equals {@code target}.
     * Null when no match (peer list was refreshed mid-request, or the target left the
     * cluster between hash-selection and forward). Caller treats null as a transport error.
     */
    private ManagedChannel findChannelForAddress(final Address target) {
        if (target == null) {
            return null;
        }
        for (final RemoteClient peer : remoteClientManager.getRemoteClient()) {
            final Address peerAddr = peer.getAddress();
            if (peerAddr != null && peerAddr.equals(target)) {
                return peer.getChannel();
            }
        }
        return null;
    }
}
