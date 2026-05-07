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
import java.util.List;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Maintains {@link ManagedChannel} connections to every reachable OAP peer at
 * the admin-internal gRPC port. Module service exposed by {@code admin-server};
 * admin features ({@code dsl-debugging}, {@code receiver-runtime-rule}) consume
 * it for peer-to-peer cluster RPCs.
 *
 * <h2>Why a dedicated channel manager</h2>
 * The cluster module's {@code RemoteClientManager} dials peers at the public
 * agent / cluster gRPC port ({@code core.gRPCPort}, default 11800). Carrying
 * privileged admin RPCs over that channel would put them in the same blast
 * radius as agent telemetry: anyone with reach to the agent network could
 * potentially invoke install / Suspend / Forward etc. Admin features use
 * THIS manager instead — peers are dialed at the admin-internal port
 * ({@code admin-server.gRPCPort}, default 17129), which operators bind to
 * a private peer-to-peer interface.
 *
 * <h2>Peer discovery</h2>
 * Hosts come from the cluster module's existing peer registry (zookeeper /
 * k8s / nacos / standalone). Each peer's host is taken from the cluster
 * registration; the port is the LOCAL admin-server config's
 * {@code gRPCPort} — uniform across the cluster by convention. If a
 * deployment uses non-uniform admin ports the cluster registration would
 * need to publish the admin port per-peer, which is out of scope for the
 * current phase.
 *
 * <h2>Channel lifecycle</h2>
 * Channels are lazily built on first use per peer and cached. A cluster
 * membership change (peer leaves) closes its channel; a new peer joining
 * gets a fresh channel on next call. Self is filtered out by
 * {@link Peer#isSelf()}.
 */
public interface AdminClusterChannelManager extends Service {

    /**
     * @return snapshot of every reachable peer's admin-side {@link Peer}.
     *         Self is included with {@link Peer#isSelf()} {@code true}; callers
     *         that fan out RPCs filter it out themselves.
     */
    List<Peer> getPeers();

    /**
     * Default per-call timeout (ms) for admin-internal RPCs, sourced from
     * {@code admin-server.internalCommunicationTimeout}. Cluster clients that
     * fan out install / collect / stop calls use this as the deadline; workflow-
     * specific call sites (runtime-rule Suspend / Forward) keep their own
     * tuned values.
     */
    long getInternalCommunicationTimeoutMs();

    /**
     * Per-peer view consumed by RPC fan-out helpers (dsl-debugging
     * {@code ClusterPeerCaller}, runtime-rule {@code RuntimeRuleClusterClient}).
     * The {@link #getChannel} return is {@code null} when the channel hasn't
     * been established yet — callers report a transient {@code channel_not_ready}
     * outcome and the next fan-out picks the channel up.
     */
    interface Peer {
        /** Display address ({@code host:port}). */
        String getAddress();

        /** {@code true} when this entry is the local OAP node. */
        boolean isSelf();

        /** {@code null} until the channel is dialed. */
        ManagedChannel getChannel();
    }
}
