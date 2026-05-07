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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;

/**
 * Per-peer fan-out helper. Same shape every cluster RPC needs — iterate
 * {@link AdminClusterChannelManager#getPeers()}, skip self, invoke the
 * per-peer body under the per-call deadline, wrap the result (or the
 * failure reason) into a {@link PeerOutcome}. Returning a list of typed
 * outcomes lets the receiving REST handler aggregate without scattered
 * try/catch or null-check branches.
 *
 * <p>Channels come from the admin-internal gRPC bus (default port 17129),
 * NOT the public agent / cluster bus (default 11800). See
 * {@link AdminClusterChannelManager} for the rationale.
 *
 * <p>Fan-out runs in parallel across peers, with each per-peer call still
 * carrying its own gRPC deadline. The wall-clock latency of the whole fan-out
 * is therefore bounded by the slowest reachable peer's deadline, not the
 * sum across peers — important for collect on a partitioned 10-node cluster
 * where a sequential fan-out would multiply the deadline per missed peer.
 */
@Slf4j
public final class ClusterPeerCaller {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final AdminClusterChannelManager peerChannelManager;
    private final String selfNodeId;

    /**
     * Bounded executor shared by every fan-out from this caller. Each task is
     * one peer's blocking gRPC call, so threads are I/O-bound rather than
     * CPU-bound; a fixed pool sized to the typical peer count is enough and
     * caps the worst-case live-thread footprint when many fan-outs queue up.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(16, r -> {
        final Thread t = new Thread(r, "admin-cluster-fanout-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public ClusterPeerCaller(final AdminClusterChannelManager peerChannelManager,
                             final String selfNodeId) {
        this.peerChannelManager = peerChannelManager;
        this.selfNodeId = selfNodeId;
    }

    public String getSelfNodeId() {
        return selfNodeId;
    }

    /**
     * Fan out one RPC call to every non-self peer in parallel.
     *
     * @param description short description (logged on failure, included in failure detail)
     * @param body        per-peer body — receives the peer's {@link ManagedChannel} (never
     *                    null; the helper skips peers without an established channel) and
     *                    returns the typed ack payload. Throwing from this body is folded
     *                    into a {@link PeerOutcome#failed(String, String)} entry.
     */
    public <A> List<PeerOutcome<A>> fanOut(final String description,
                                           final Function<ManagedChannel, A> body) {
        final List<AdminClusterChannelManager.Peer> peers = peerChannelManager.getPeers();
        // Parallel arrays so a future that times out / errors / gets cancelled
        // can still report the peer's address for diagnostics — losing peer
        // identity to a generic "unknown" entry is the worst possible thing
        // for an operator triaging which OAP failed.
        final List<AdminClusterChannelManager.Peer> dispatched = new ArrayList<>(peers.size());
        final List<CompletableFuture<PeerOutcome<A>>> futures = new ArrayList<>(peers.size());
        for (final AdminClusterChannelManager.Peer peer : peers) {
            if (peer.isSelf()) {
                continue;
            }
            dispatched.add(peer);
            futures.add(CompletableFuture.supplyAsync(
                () -> invokeOne(peer, description, body), executor));
        }
        final long globalDeadlineMs = peerChannelManager.getInternalCommunicationTimeoutMs();
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(globalDeadlineMs);
        final List<PeerOutcome<A>> outcomes = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            final CompletableFuture<PeerOutcome<A>> future = futures.get(i);
            final String peerAddress = dispatched.get(i).getAddress();
            final long remaining = Math.max(0L, deadlineNanos - System.nanoTime());
            try {
                outcomes.add(future.get(remaining, TimeUnit.NANOSECONDS));
            } catch (final TimeoutException te) {
                future.cancel(true);
                outcomes.add(PeerOutcome.failed(peerAddress, "fanout_global_deadline_exceeded"));
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                outcomes.add(PeerOutcome.failed(peerAddress, "interrupted"));
            } catch (final ExecutionException ee) {
                outcomes.add(PeerOutcome.failed(peerAddress, ee.getCause() == null
                    ? ee.getMessage() : ee.getCause().getMessage()));
            }
        }
        return outcomes;
    }

    private <A> PeerOutcome<A> invokeOne(final AdminClusterChannelManager.Peer peer, final String description,
                                          final Function<ManagedChannel, A> body) {
        final String peerAddress = peer.getAddress();
        final ManagedChannel channel = peer.getChannel();
        if (channel == null) {
            log.warn("DSL debug {} skipped for peer {}: channel not yet established",
                     description, peerAddress);
            return PeerOutcome.failed(peerAddress, "channel_not_ready");
        }
        try {
            final A ack = body.apply(channel);
            if (ack == null) {
                return PeerOutcome.failed(peerAddress, "null_response");
            }
            return PeerOutcome.ok(peerAddress, ack);
        } catch (final Throwable t) {
            log.warn("DSL debug {} to peer {} failed: {}", description, peerAddress, t.getMessage());
            return PeerOutcome.failed(peerAddress, t.getMessage());
        }
    }
}
