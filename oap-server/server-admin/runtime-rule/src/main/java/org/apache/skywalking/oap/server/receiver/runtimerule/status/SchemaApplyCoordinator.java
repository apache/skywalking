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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;

/**
 * Single in-memory owner of runtime-rule apply progress on the cluster main. Each apply opens a
 * status entry keyed by a generated {@code applyId} and advances it through {@link ApplyPhase}
 * as the apply runs (validate → DDL → fence → roll out → applied), or to a terminal
 * {@link ApplyPhase#FAILED} / {@link ApplyPhase#DEGRADED}.
 *
 * <p>Two indexes back the two ways a caller asks "is this live yet?":
 * <ul>
 *   <li>by {@code applyId} — the live handle a just-submitted apply polls;</li>
 *   <li>by {@code (catalog, name)} → latest applyId — the content-based path used when the
 *       apply-id is gone (page refresh / main restart), resolved against the durable content hash.</li>
 * </ul>
 *
 * <p>Concurrency: a {@link ConcurrentHashMap} of immutable {@link ApplyStatus} snapshots. Writes
 * for a given apply are effectively single-threaded (the apply orchestration serializes per file),
 * reads (progress queries) are concurrent and lock-free against the snapshot. This phase is the
 * building block; the apply-lifecycle wiring, per-node breakdown, the {@code GetApplyStatus} query
 * surface, and a background convergence watch with TTL eviction layer on later. State is in-memory
 * by design — the durable content hash reconstructs truth after a main restart.
 */
@Slf4j
public class SchemaApplyCoordinator {

    /**
     * Process-wide instance the production apply / cluster-RPC / reconcile paths share, mirroring
     * the {@code MetadataRegistry.INSTANCE} / {@code DSLClassLoaderManager.INSTANCE} pattern so the
     * coordinator need not be threaded through every constructor. Uses the wall-clock; tests
     * construct their own instance with an injected clock instead.
     */
    public static final SchemaApplyCoordinator INSTANCE = new SchemaApplyCoordinator();

    private final Map<String, ApplyStatus> byApplyId = new ConcurrentHashMap<>();
    private final Map<String, String> latestApplyIdByFile = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public SchemaApplyCoordinator() {
        this(System::currentTimeMillis);
    }

    /** Clock-injectable for deterministic tests (and the timed background watch added later). */
    public SchemaApplyCoordinator(final LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Open a new apply in {@link ApplyPhase#PENDING} and return its {@code applyId}. Records it as
     * the latest apply for {@code (catalog, name)} so a content-based query resolves to it.
     */
    public String begin(final String catalog, final String name, final String contentHash) {
        final String applyId = UUID.randomUUID().toString();
        final long now = clock.getAsLong();
        byApplyId.put(applyId, new ApplyStatus(
            applyId, catalog, name, contentHash, ApplyPhase.PENDING, null, now, now));
        latestApplyIdByFile.put(fileKey(catalog, name), applyId);
        if (log.isDebugEnabled()) {
            log.debug("apply [{}] begin: {}/{} hash={}", applyId, catalog, name, contentHash);
        }
        return applyId;
    }

    /** Advance an apply to {@code phase} (forward progress; clears any prior failure reason). No-op
     *  for an unknown apply-id. */
    public void transition(final String applyId, final ApplyPhase phase) {
        update(applyId, s -> s.withPhase(phase, clock.getAsLong()));
    }

    /** Terminal success. */
    public void markApplied(final String applyId) {
        transition(applyId, ApplyPhase.APPLIED);
    }

    /** Terminal: committed and durable, but cluster-wide propagation unconfirmed within budget
     *  (a node is lagging). Not a revert — a background re-check may flip it to APPLIED later. */
    public void markDegraded(final String applyId, final String reason) {
        update(applyId, s -> s.withFailure(ApplyPhase.DEGRADED, reason, clock.getAsLong()));
    }

    /** Terminal: a pre-commit error (compile / verify / DDL RPC / persist); the change was rolled
     *  back. */
    public void markFailed(final String applyId, final String reason) {
        update(applyId, s -> s.withFailure(ApplyPhase.FAILED, reason, clock.getAsLong()));
    }

    private void update(final String applyId, final UnaryOperator<ApplyStatus> op) {
        byApplyId.computeIfPresent(applyId, (k, s) -> op.apply(s));
    }

    /** The live status for an apply-id, or {@code null} when the main no longer holds it (caller
     *  treats null as {@link ApplyPhase#UNKNOWN} and falls back to the content-based path). */
    public ApplyStatus get(final String applyId) {
        return byApplyId.get(applyId);
    }

    /**
     * The latest apply status for a file, for the content-based query when the apply-id is unknown.
     * Returns {@code null} if no apply for that file is tracked, or if {@code expectedContentHash}
     * is non-null and does not match the latest apply's hash (the latest tracked apply is for a
     * different content than the caller asked about).
     */
    public ApplyStatus getLatestByFile(final String catalog, final String name,
                                       final String expectedContentHash) {
        final String applyId = latestApplyIdByFile.get(fileKey(catalog, name));
        if (applyId == null) {
            return null;
        }
        final ApplyStatus status = byApplyId.get(applyId);
        if (status == null) {
            return null;
        }
        if (expectedContentHash != null && !expectedContentHash.equals(status.getContentHash())) {
            return null;
        }
        return status;
    }

    /** Number of tracked applies — for tests and the future TTL-eviction watch. */
    public int trackedCount() {
        return byApplyId.size();
    }

    private static String fileKey(final String catalog, final String name) {
        return catalog + "/" + name;
    }
}
