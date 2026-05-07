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

package org.apache.skywalking.oap.server.admin.server.cluster;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;

/**
 * {@link AdminClusterChannelManager} backed by the cluster module's
 * {@link ClusterNodesQuery} for peer discovery. Hosts come from the cluster
 * registry; each peer is dialed at the LOCAL admin gRPC port (uniform
 * across the cluster by convention). A background reconciler runs every
 * {@code refreshIntervalMs} to open channels for new peers and shut down
 * channels for departed peers.
 *
 * <p>Self detection: the cluster registry includes the local node and
 * stamps {@link Address#isSelf()} on the local entry. We never open a
 * channel to self — the {@link Peer#getChannel()} for the self entry
 * returns {@code null}, and fan-out callers filter via
 * {@link Peer#isSelf()} before calling.
 *
 * <p>Why a separate manager instead of riding {@code RemoteClientManager}:
 * see {@link AdminClusterChannelManager} javadoc — {@code RemoteClientManager}
 * dials peers at the public agent gRPC port; admin RPCs MUST stay off
 * that channel.
 */
@Slf4j
public class AdminClusterChannelManagerImpl implements AdminClusterChannelManager {

    private static final long REFRESH_INTERVAL_MS = 5_000L;

    /**
     * Resolved lazily on first reconcile pass to support the framework's
     * {@code requiredCheck} ordering. The check fires BEFORE every provider's
     * {@code start()}, so this manager has to be REGISTERED from
     * {@code prepare()} (else the admin-server provider fails its own
     * required-services check). But {@link ClusterNodesQuery} comes from
     * the cluster module, which isn't guaranteed to have its services
     * available during admin-server's {@code prepare()}. The supplier
     * defers the lookup until first reconcile, which fires from
     * {@code notifyAfterCompleted()} after every module's {@code start()}.
     */
    private final Supplier<ClusterNodesQuery> clusterNodesQuerySupplier;
    private volatile ClusterNodesQuery clusterNodesQuery;
    private final int adminGrpcPort;
    private final long internalCommunicationTimeoutMs;
    private final SslContext clientSslContext;

    /** Indexed by {@code host:adminPort}; access guarded by {@code synchronized(this)}. */
    private final Map<String, ManagedChannel> channels = new HashMap<>();
    private volatile List<Peer> snapshot = Collections.emptyList();

    private final ScheduledExecutorService reconciler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
                final Thread t = new Thread(r, "admin-peer-channel-reconciler");
                t.setDaemon(true);
                return t;
            });

    public AdminClusterChannelManagerImpl(final Supplier<ClusterNodesQuery> clusterNodesQuerySupplier,
                                          final int adminGrpcPort,
                                          final long internalCommunicationTimeoutMs,
                                          final SslContext clientSslContext) {
        this.clusterNodesQuerySupplier = clusterNodesQuerySupplier;
        this.adminGrpcPort = adminGrpcPort;
        this.internalCommunicationTimeoutMs = internalCommunicationTimeoutMs;
        this.clientSslContext = clientSslContext;
    }

    @Override
    public long getInternalCommunicationTimeoutMs() {
        return internalCommunicationTimeoutMs;
    }

    /** Build the TLS context callers need to pass when {@code admin-server.gRPCSslEnabled=true}. */
    public static SslContext clientSslContext(final String trustedCAsPath) throws SSLException {
        return GrpcSslContexts.forClient()
                              .trustManager(new File(trustedCAsPath))
                              .build();
    }

    /**
     * Run the first reconcile and schedule subsequent passes on the
     * background thread. Called by {@code AdminServerModuleProvider}'s
     * {@code notifyAfterCompleted} — never directly from feature modules.
     */
    public void start() {
        reconcile();
        reconciler.scheduleWithFixedDelay(this::reconcileQuiet,
            REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the reconciler and close every open peer channel. Called on
     * OAP shutdown via the module's lifecycle (or by tests that reuse the
     * manager across phases).
     */
    public void shutdown() {
        reconciler.shutdownNow();
        synchronized (this) {
            for (final ManagedChannel c : channels.values()) {
                shutdownChannel(c);
            }
            channels.clear();
            snapshot = Collections.emptyList();
        }
    }

    @Override
    public List<Peer> getPeers() {
        return snapshot;
    }

    private void reconcileQuiet() {
        try {
            reconcile();
        } catch (final Throwable t) {
            log.warn("admin peer channel reconcile failed", t);
        }
    }

    /**
     * One reconcile pass:
     * <ol>
     *   <li>Pull the current peer list from {@link ClusterNodesQuery}</li>
     *   <li>For new peers, dial a {@link ManagedChannel} at the admin port</li>
     *   <li>For departed peers, shut down their channel</li>
     *   <li>Publish a fresh immutable snapshot</li>
     * </ol>
     */
    private synchronized void reconcile() {
        if (clusterNodesQuery == null) {
            clusterNodesQuery = clusterNodesQuerySupplier.get();
        }
        final List<RemoteInstance> nodes = clusterNodesQuery.queryRemoteNodes();
        final Set<String> live = new HashSet<>();
        final List<Peer> next = new ArrayList<>(nodes.size());

        for (final RemoteInstance node : nodes) {
            final String host = node.getAddress().getHost();
            final boolean isSelf = node.getAddress().isSelf();
            final String key = host + ":" + adminGrpcPort;
            live.add(key);

            ManagedChannel channel = null;
            if (!isSelf) {
                channel = channels.get(key);
                if (channel == null) {
                    channel = buildChannel(host, adminGrpcPort);
                    if (channel != null) {
                        channels.put(key, channel);
                        log.info("admin peer channel opened to {}", key);
                    }
                }
            }
            next.add(new ImmutablePeer(key, isSelf, channel));
        }

        // Close channels for peers no longer in the cluster.
        final List<String> stale = new ArrayList<>();
        for (final String key : channels.keySet()) {
            if (!live.contains(key)) {
                stale.add(key);
            }
        }
        for (final String key : stale) {
            shutdownChannel(channels.remove(key));
            log.info("admin peer channel closed to {}", key);
        }

        snapshot = Collections.unmodifiableList(next);
    }

    private ManagedChannel buildChannel(final String host, final int port) {
        try {
            final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
            if (clientSslContext != null) {
                builder.useTransportSecurity().sslContext(clientSslContext);
            } else {
                builder.usePlaintext();
            }
            return builder.build();
        } catch (final Throwable t) {
            log.warn("failed to build admin peer channel to {}:{} — will retry next reconcile",
                     host, port, t);
            return null;
        }
    }

    private static void shutdownChannel(final ManagedChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Throwable t) {
            log.warn("admin peer channel shutdown raised", t);
        }
    }

    private static final class ImmutablePeer implements Peer {
        private final String address;
        private final boolean isSelf;
        private final ManagedChannel channel;

        ImmutablePeer(final String address, final boolean isSelf,
                      final ManagedChannel channel) {
            this.address = address;
            this.isSelf = isSelf;
            this.channel = channel;
        }

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public boolean isSelf() {
            return isSelf;
        }

        @Override
        public ManagedChannel getChannel() {
            return channel;
        }
    }
}
