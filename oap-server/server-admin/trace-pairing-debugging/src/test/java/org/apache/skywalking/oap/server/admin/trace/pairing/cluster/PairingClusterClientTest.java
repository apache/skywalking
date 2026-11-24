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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CaptureAck;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.StartCaptureRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.TracePairingDebuggingClusterServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairingClusterClientTest {
    private static final long TIMEOUT_MILLIS = 800L;

    private final List<Server> servers = new ArrayList<>();
    private final List<ManagedChannel> channels = new ArrayList<>();

    @AfterEach
    void tearDown() {
        channels.forEach(ManagedChannel::shutdownNow);
        servers.forEach(Server::shutdownNow);
    }

    @Test
    void stalledPeersDelayNoOtherPeersCall() throws Exception {
        final List<AdminClusterChannelManager.Peer> peers = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            peers.add(peer("stalled-" + i, false));
        }
        peers.add(peer("prompt", true));
        final PairingClusterClient client = new PairingClusterClient(new TestPeers(peers));

        final long began = System.nanoTime();
        final List<PeerOutcome<CaptureAck>> outcomes = client.start("r1", 1L);
        final long tookMillis = (System.nanoTime() - began) / 1_000_000L;

        assertEquals(10, outcomes.size());
        final List<String> answered = outcomes.stream().filter(PeerOutcome::isOk).map(PeerOutcome::getPeer)
                                              .collect(Collectors.toList());
        assertEquals(List.of("prompt"), answered, "the prompt peer answers though nine others stall");
        assertTrue(tookMillis < TIMEOUT_MILLIS * 3, "one budget for all, took " + tookMillis + " ms");
    }

    @Test
    void theNodeNamesItselfByTheAddressTheOthersKnowItBy() {
        final AdminClusterChannelManager.Peer self = new AdminClusterChannelManager.Peer() {
            @Override
            public String getAddress() {
                return "10.0.0.5:17129";
            }

            @Override
            public boolean isSelf() {
                return true;
            }

            @Override
            public ManagedChannel getChannel() {
                return null;
            }
        };
        assertEquals("10.0.0.5:17129", new PairingClusterClient(new TestPeers(List.of(self))).selfAddress());
        assertNull(new PairingClusterClient(new TestPeers(new ArrayList<>())).selfAddress(), "alone, no address");
    }

    private AdminClusterChannelManager.Peer peer(final String name, final boolean answers) throws Exception {
        servers.add(InProcessServerBuilder.forName(name).directExecutor().addService(
            new TracePairingDebuggingClusterServiceGrpc.TracePairingDebuggingClusterServiceImplBase() {
                @Override
                public void startCapture(final StartCaptureRequest request,
                                         final StreamObserver<CaptureAck> responseObserver) {
                    if (answers) {
                        responseObserver.onNext(CaptureAck.newBuilder().setNodeId(name).build());
                        responseObserver.onCompleted();
                    }
                    // A stalled peer never answers; the caller's deadline ends the call.
                }
            }).build().start());
        final ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        channels.add(channel);
        return new AdminClusterChannelManager.Peer() {
            @Override
            public String getAddress() {
                return name;
            }

            @Override
            public boolean isSelf() {
                return false;
            }

            @Override
            public ManagedChannel getChannel() {
                return channel;
            }
        };
    }

    private static final class TestPeers implements AdminClusterChannelManager {
        private final List<Peer> peers;

        private TestPeers(final List<Peer> peers) {
            this.peers = peers;
        }

        @Override
        public List<Peer> getPeers() {
            return peers;
        }

        @Override
        public long getInternalCommunicationTimeoutMs() {
            return TIMEOUT_MILLIS;
        }

        @Override
        public int getMaxMessageSize() {
            return 0;
        }
    }
}
