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
 */

package org.apache.skywalking.oap.server.admin.trace.pairing.cluster;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CaptureAck;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CollectPairingsRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CollectPairingsResponse;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.StartCaptureRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.StopCaptureRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.TracePairingDebuggingClusterServiceGrpc;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.TracePairingDebuggingClusterServiceGrpc.TracePairingDebuggingClusterServiceFutureStub;

/**
 * Calls every other OAP node on the admin-internal bus at once, each call with the bus's timeout from when it was
 * sent, and collects them within that one budget: a slow node delays no other node's call.
 */
@Slf4j
public final class PairingClusterClient {
    private final AdminClusterChannelManager channels;
    private final long timeoutMillis;

    public PairingClusterClient(final AdminClusterChannelManager channels) {
        this.channels = channels;
        this.timeoutMillis = channels.getInternalCommunicationTimeoutMs();
    }

    public List<PeerOutcome<CaptureAck>> start(final String roundId, final long startTime) {
        final StartCaptureRequest request = StartCaptureRequest.newBuilder()
            .setRoundId(roundId).setStartTime(startTime).build();
        return fanOut("start", stub -> stub.startCapture(request));
    }

    /**
     * @return this node's address on the admin bus, as the other nodes name it, standalone included; null when the
     *     cluster registry does not list this node
     */
    public String selfAddress() {
        for (final AdminClusterChannelManager.Peer peer : channels.getPeers()) {
            if (peer.isSelf()) {
                return peer.getAddress();
            }
        }
        return null;
    }

    public List<PeerOutcome<CaptureAck>> stop() {
        return fanOut("stop", stub -> stub.stopCapture(StopCaptureRequest.getDefaultInstance()));
    }

    public List<PeerOutcome<CollectPairingsResponse>> pairings() {
        return fanOut("collect pairings", stub -> stub.collectPairings(CollectPairingsRequest.getDefaultInstance()));
    }

    private <A> List<PeerOutcome<A>> fanOut(
        final String what, final Function<TracePairingDebuggingClusterServiceFutureStub, ListenableFuture<A>> call) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final List<PeerOutcome<A>> outcomes = new ArrayList<>();
        final List<String> addresses = new ArrayList<>();
        final List<ListenableFuture<A>> sent = new ArrayList<>();
        for (final AdminClusterChannelManager.Peer peer : channels.getPeers()) {
            if (peer.isSelf()) {
                continue;
            }
            final ManagedChannel channel = peer.getChannel();
            if (channel == null) {
                outcomes.add(PeerOutcome.failed(peer.getAddress(), "channel not ready"));
                continue;
            }
            try {
                sent.add(call.apply(TracePairingDebuggingClusterServiceGrpc.newFutureStub(channel)
                                                                           .withDeadlineAfter(timeoutMillis,
                                                                               TimeUnit.MILLISECONDS)));
                addresses.add(peer.getAddress());
            } catch (final RuntimeException e) {
                outcomes.add(PeerOutcome.failed(peer.getAddress(), e.getMessage()));
            }
        }
        for (int i = 0; i < sent.size(); i++) {
            final ListenableFuture<A> future = sent.get(i);
            try {
                outcomes.add(PeerOutcome.ok(addresses.get(i),
                    future.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)));
            } catch (final TimeoutException e) {
                future.cancel(true);
                outcomes.add(PeerOutcome.failed(addresses.get(i), "timeout"));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                outcomes.add(PeerOutcome.failed(addresses.get(i), "interrupted"));
            } catch (final ExecutionException e) {
                log.warn("Trace pairing debugging {} on peer {} failed: {}", what, addresses.get(i),
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                outcomes.add(PeerOutcome.failed(addresses.get(i),
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
            }
        }
        return outcomes;
    }
}
