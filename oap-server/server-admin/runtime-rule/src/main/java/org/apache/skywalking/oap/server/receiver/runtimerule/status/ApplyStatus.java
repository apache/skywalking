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

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Immutable snapshot of one runtime-rule apply's progress on the cluster main. Held by
 * {@link SchemaApplyCoordinator}; transitions produce a new snapshot (copy-on-write) so concurrent
 * readers (a progress query) never see a torn value.
 *
 * <p>{@code contentHash} is the durable identity of the applied file — a status query can be
 * answered by it even after the ephemeral {@code applyId} is gone. {@code failureReason} is set
 * only for {@link ApplyPhase#FAILED} / {@link ApplyPhase#DEGRADED}.
 *
 * <p>This snapshot carries the apply-level (main-orchestrated) phase. Per-node breakdown
 * (storage-plane laggards, per-OAP-node applied state) is layered on in a later phase via the
 * status query DTO; this type stays a small, immutable core.
 */
@Getter
public final class ApplyStatus {
    private final String applyId;
    private final String catalog;
    private final String name;
    private final String contentHash;
    private final ApplyPhase phase;
    /** Non-null only for {@link ApplyPhase#FAILED} (pre-commit error) and {@link ApplyPhase#DEGRADED}
     *  (committed but a node lagging at fence timeout, or local commit-tail threw). Null otherwise. */
    private final String failureReason;
    private final long startedAtMs;
    private final long updatedAtMs;
    /** Data-node ids that had not confirmed the new schema revision when the fence timed out.
     *  Non-empty only for {@link ApplyPhase#DEGRADED} caused by fence non-confirmation; always a
     *  non-null (possibly empty) immutable list. */
    private final List<String> fenceLaggards;

    public ApplyStatus(final String applyId, final String catalog, final String name,
                       final String contentHash, final ApplyPhase phase, final String failureReason,
                       final long startedAtMs, final long updatedAtMs, final List<String> fenceLaggards) {
        this.applyId = applyId;
        this.catalog = catalog;
        this.name = name;
        this.contentHash = contentHash;
        this.phase = phase;
        this.failureReason = failureReason;
        this.startedAtMs = startedAtMs;
        this.updatedAtMs = updatedAtMs;
        this.fenceLaggards = fenceLaggards == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(fenceLaggards);
    }

    /** A copy advanced to {@code newPhase}, clearing any prior failure reason + laggards (forward
     *  progress). */
    public ApplyStatus withPhase(final ApplyPhase newPhase, final long nowMs) {
        return new ApplyStatus(applyId, catalog, name, contentHash, newPhase, null, startedAtMs, nowMs,
            Collections.emptyList());
    }

    /** A copy moved to a non-success terminal ({@link ApplyPhase#FAILED} / {@link ApplyPhase#DEGRADED})
     *  carrying {@code reason} and no laggards. */
    public ApplyStatus withFailure(final ApplyPhase terminalPhase, final String reason, final long nowMs) {
        return withFailure(terminalPhase, reason, Collections.emptyList(), nowMs);
    }

    /** A copy moved to a non-success terminal carrying {@code reason} and the {@code laggards} that
     *  caused a fence-non-confirmation {@link ApplyPhase#DEGRADED}. */
    public ApplyStatus withFailure(final ApplyPhase terminalPhase, final String reason,
                                   final List<String> laggards, final long nowMs) {
        return new ApplyStatus(applyId, catalog, name, contentHash, terminalPhase, reason, startedAtMs, nowMs,
            laggards);
    }
}
