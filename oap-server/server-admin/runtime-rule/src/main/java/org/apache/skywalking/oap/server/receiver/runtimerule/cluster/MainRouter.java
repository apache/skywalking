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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;

/**
 * Stateless main-node selector. The runtime-rule HTTP write workflow runs on
 * exactly ONE OAP node per cluster — "the main" — to serialize compile + DDL +
 * persist for a {@code (catalog, name)} pair. Non-main nodes that receive a
 * write request forward it to the main via the cluster Forward RPC.
 *
 * <p>Selection: the peers reported by {@link AdminClusterChannelManager} are
 * sorted by address (host:adminPort string), and the FIRST entry is the main.
 * Same shape as the prior cluster-bus router, just routed off the
 * admin-internal channel pool instead of the public agent / cluster bus.
 *
 * <h2>Public-contract constraint: single-main routing</h2>
 *
 * <p>This is intentional, not an oversight. ALL admin writes (addOrUpdate /
 * inactivate / delete), schema work (DDL pushes against BanyanDB / ES / JDBC),
 * and forwarded RPCs centralize on the same OAP. That OAP is the cluster's
 * sorted-first peer — stable across nodes, deterministic, no leader election.
 * Operators behind a load balancer in front of the admin port see no behaviour
 * change: non-main targets transparently forward.
 *
 * <p><b>Trade-offs operators should plan for:</b>
 * <ul>
 *   <li>Write throughput is bounded by the main's CPU / DAO latency, not the
 *       cluster's. Runtime-rule writes are operator-scale (manual {@code curl}s
 *       or UI clicks against a control plane), not data-plane scale, so this is
 *       acceptable by design.</li>
 *   <li>If the main goes down, the next sorted-first peer takes over on the
 *       next reconcile tick. In-flight writes against the dead main fail fast
 *       (the receiver returns {@code 503 main_unreachable}); operators retry.</li>
 *   <li>(catalog, name)-keyed sharding is deliberately NOT implemented — it
 *       would distribute write load but introduce per-rule ownership
 *       hand-offs whose failure modes (split-brain, stale ownership records)
 *       are far costlier to operate than the simple bottleneck this contract
 *       accepts.</li>
 * </ul>
 *
 * <p>If your deployment ever needs cross-rule write parallelism, the
 * forwarding hop is the right place to add (catalog, name)-keyed routing — but
 * the public contract today is single-main, and any caller (REST, sync tick,
 * peer Forward) can rely on it.
 */
public final class MainRouter {

    private MainRouter() {
    }

    /**
     * First peer in the sorted list — that's the main. Null when the cluster is empty
     * (single-node embedded topology / early boot). Callers treat null as "self is the only
     * node, so self is main".
     */
    public static AdminClusterChannelManager.Peer mainPeer(final AdminClusterChannelManager apm) {
        if (apm == null) {
            return null;
        }
        final List<AdminClusterChannelManager.Peer> peers = apm.getPeers();
        if (peers == null || peers.isEmpty()) {
            return null;
        }
        final List<AdminClusterChannelManager.Peer> sorted = new ArrayList<>(peers);
        sorted.sort((a, b) -> a.getAddress().compareTo(b.getAddress()));
        return sorted.get(0);
    }

    /** Main's address ({@code host:adminPort} string). Null when the cluster is empty. */
    public static String mainAddress(final AdminClusterChannelManager apm) {
        final AdminClusterChannelManager.Peer main = mainPeer(apm);
        return main == null ? null : main.getAddress();
    }

    /**
     * True if this node is the main, i.e. the first-sorted peer is self (or the cluster is
     * empty, in which case self is trivially the only valid main). Callers use this as the
     * gate before acquiring per-file locks + running the write workflow; non-main requests
     * get forwarded via the cluster bus to the main.
     */
    public static boolean isSelfMain(final AdminClusterChannelManager apm) {
        final AdminClusterChannelManager.Peer main = mainPeer(apm);
        return main == null || main.isSelf();
    }

    /** Snapshot of all peers including self, sorted by address. */
    public static List<AdminClusterChannelManager.Peer> sortedPeers(final AdminClusterChannelManager apm) {
        if (apm == null || apm.getPeers() == null) {
            return Collections.emptyList();
        }
        final List<AdminClusterChannelManager.Peer> sorted = new ArrayList<>(apm.getPeers());
        sorted.sort((a, b) -> a.getAddress().compareTo(b.getAddress()));
        return sorted;
    }
}
