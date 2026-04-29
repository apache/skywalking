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

import java.util.List;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;

/**
 * Cluster-wide selector for the single "runtime-rule main" OAP. The main is the first entry
 * in {@link RemoteClientManager#getRemoteClient()} — which {@code RemoteClientManager} keeps
 * sorted by {@link Address} natural ordering (host:port). Every OAP sees the same sorted
 * list → every OAP agrees on the same main. The main changes only when cluster topology
 * changes (the lexicographically-first node joins or leaves).
 *
 * <p>This matches the {@link org.apache.skywalking.oap.server.core.remote.selector.ForeverFirstSelector}
 * strategy used by {@code RemoteSenderService} for other always-first routing needs; we don't
 * go through that service because we don't need the selector-cache plumbing, just the
 * "who's first" answer.
 *
 * <p>Why single-main (not per-file hash): at runtime-rule scale — dozens of files, a handful
 * of operator pushes per day — the simplicity of "one writer at a time" beats the throughput
 * gain of distributing writes across nodes. Single-main also makes the
 * {@link org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState.SuspendOrigin BOTH}
 * origin a hard impossibility under correct routing, so its appearance immediately signals
 * split-brain without needing per-file analysis.
 *
 * <p>Routing is advisory on the REST side: a non-main OAP that receives a write forwards the
 * request to the main via the cluster bus (see {@code Forward} RPC). The fail-safe path —
 * non-main receives a forwarded request from a node that also thought IT wasn't main —
 * short-circuits with HTTP 421 to bound cluster ping-pong at one hop.
 */
public final class MainRouter {

    private MainRouter() {
    }

    /**
     * First client in the sorted peer list — that's the main. Null when the cluster is empty
     * (single-node embedded topology / early boot). Callers treat null as "self is the only
     * node, so self is main".
     */
    public static RemoteClient mainClient(final RemoteClientManager rcm) {
        if (rcm == null) {
            return null;
        }
        final List<RemoteClient> peers = rcm.getRemoteClient();
        if (peers == null || peers.isEmpty()) {
            return null;
        }
        return peers.get(0);
    }

    /** Main's address. Null when the cluster is empty. */
    public static Address mainAddress(final RemoteClientManager rcm) {
        final RemoteClient main = mainClient(rcm);
        return main == null ? null : main.getAddress();
    }

    /**
     * True if this node is the main, i.e. the first-sorted peer is self (or the cluster is
     * empty, in which case self is trivially the only valid main). Callers use this as the
     * gate before acquiring per-file locks + running the write workflow; non-main requests
     * get forwarded via the cluster bus to the main.
     */
    public static boolean isSelfMain(final RemoteClientManager rcm) {
        final Address main = mainAddress(rcm);
        return main == null || main.isSelf();
    }
}
