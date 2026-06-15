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

package org.apache.skywalking.oap.server.receiver.runtimerule.status;

/**
 * Lifecycle phase of one runtime-rule apply, as tracked by {@link SchemaApplyCoordinator} on the
 * cluster main. A progress query (added in a later phase) reports this so an operator can see how
 * far an apply got — and, when it stops short, whether it is converging ({@link #DEGRADED}) or
 * failed outright ({@link #FAILED}).
 *
 * <p>Normal progression:
 * {@link #PENDING} → {@link #DDL} → {@link #ROLLING_OUT} → {@link #FENCING} → {@link #APPLIED}.
 * These are the phases the main observes from its apply orchestration: {@code PENDING} once the
 * apply is accepted, {@code DDL} while the compile/verify/schema-change call runs (a single opaque
 * step from the orchestrator's vantage — sub-steps such as validation are not separately
 * observable, so they are not modelled), {@code ROLLING_OUT} once the rule row is durably persisted
 * and the commit is draining + peers are being resumed/notified, then {@code FENCING} while the
 * main waits (in the background, on a generous timeout) for every BanyanDB data node to apply the
 * new schema revision. The HTTP response returns at {@code ROLLING_OUT} with the {@code applyId};
 * the operator polls to watch {@code FENCING → APPLIED} (or {@code DEGRADED}). Two off-ramps:
 * <ul>
 *   <li>{@link #FAILED} — a pre-commit error (compile / verify / DDL RPC / persist). The change
 *       was rolled back; nothing was committed.</li>
 *   <li>{@link #DEGRADED} — committed and durable, but the local commit-tail threw or the post-DDL
 *       fence did not confirm cluster-wide propagation within the timeout (one or more data nodes
 *       lagging — exposed as {@code fenceLaggards}). Forward-progress: peers converge from the
 *       durable row and BanyanDB keeps converging; this is NOT a revert.</li>
 * </ul>
 * {@link #UNKNOWN} is returned for an apply-id the main no longer holds (evicted / main restarted);
 * callers fall back to a content-hash comparison.
 */
public enum ApplyPhase {
    PENDING,
    DDL,
    ROLLING_OUT,
    FENCING,
    APPLIED,
    DEGRADED,
    FAILED,
    UNKNOWN;

    /**
     * A terminal phase no longer advances on its own: the apply succeeded ({@link #APPLIED}),
     * failed and rolled back ({@link #FAILED}), or committed-but-unconfirmed ({@link #DEGRADED},
     * which a background re-check may still flip to {@link #APPLIED}).
     */
    public boolean isTerminal() {
        return this == APPLIED || this == DEGRADED || this == FAILED;
    }
}
