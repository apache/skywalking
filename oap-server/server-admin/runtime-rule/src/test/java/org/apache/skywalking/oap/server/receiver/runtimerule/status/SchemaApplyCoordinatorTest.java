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

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchemaApplyCoordinatorTest {

    private final AtomicLong clock = new AtomicLong(1_000L);

    private SchemaApplyCoordinator newCoordinator() {
        return new SchemaApplyCoordinator(clock::get);
    }

    @Test
    void beginOpensPendingStatusAndIndexesByApplyIdAndFile() {
        final SchemaApplyCoordinator coord = newCoordinator();
        final String applyId = coord.begin("otel-rules", "vm", "hash-1");

        final ApplyStatus byId = coord.get(applyId);
        assertNotNull(byId, "a just-begun apply must be retrievable by apply-id");
        assertEquals(ApplyPhase.PENDING, byId.getPhase());
        assertEquals("otel-rules", byId.getCatalog());
        assertEquals("vm", byId.getName());
        assertEquals("hash-1", byId.getContentHash());
        assertEquals(1_000L, byId.getStartedAtMs());
        assertNull(byId.getFailureReason(), "a fresh apply has no failure reason");

        final ApplyStatus byFile = coord.getLatestByFile("otel-rules", "vm", null);
        assertEquals(applyId, byFile.getApplyId(), "content-based lookup must resolve to the latest apply");
    }

    @Test
    void transitionsAdvancePhaseAndStampUpdatedAt() {
        final SchemaApplyCoordinator coord = newCoordinator();
        final String applyId = coord.begin("otel-rules", "vm", "h");
        clock.set(2_000L);
        coord.transition(applyId, ApplyPhase.DDL);
        clock.set(3_000L);
        coord.transition(applyId, ApplyPhase.FENCING);

        final ApplyStatus s = coord.get(applyId);
        assertEquals(ApplyPhase.FENCING, s.getPhase());
        assertEquals(1_000L, s.getStartedAtMs(), "startedAt is fixed at begin");
        assertEquals(3_000L, s.getUpdatedAtMs(), "updatedAt advances with each transition");
    }

    @Test
    void markAppliedIsTerminalSuccess() {
        final SchemaApplyCoordinator coord = newCoordinator();
        final String applyId = coord.begin("otel-rules", "vm", "h");
        coord.markApplied(applyId);
        final ApplyStatus s = coord.get(applyId);
        assertEquals(ApplyPhase.APPLIED, s.getPhase());
        assertTrue(s.getPhase().isTerminal());
        assertNull(s.getFailureReason());
    }

    @Test
    void markFailedAndDegradedCarryReasonAndAreTerminal() {
        final SchemaApplyCoordinator coord = newCoordinator();
        final String failed = coord.begin("otel-rules", "a", "h");
        coord.markFailed(failed, "previous schema not found");
        assertEquals(ApplyPhase.FAILED, coord.get(failed).getPhase());
        assertEquals("previous schema not found", coord.get(failed).getFailureReason());
        assertTrue(coord.get(failed).getPhase().isTerminal());

        final String degraded = coord.begin("otel-rules", "b", "h");
        coord.markDegraded(degraded, "data node lagging: node-3");
        assertEquals(ApplyPhase.DEGRADED, coord.get(degraded).getPhase());
        assertEquals("data node lagging: node-3", coord.get(degraded).getFailureReason());
        assertTrue(coord.get(degraded).getPhase().isTerminal());
    }

    @Test
    void forwardTransitionClearsAStaleFailureReason() {
        // A DEGRADED apply whose background re-check later confirms convergence flips to APPLIED,
        // and the stale "lagging" reason must not linger.
        final SchemaApplyCoordinator coord = newCoordinator();
        final String applyId = coord.begin("otel-rules", "vm", "h");
        coord.markDegraded(applyId, "node-3 lagging");
        coord.markApplied(applyId);
        assertEquals(ApplyPhase.APPLIED, coord.get(applyId).getPhase());
        assertNull(coord.get(applyId).getFailureReason(),
            "advancing past DEGRADED must clear the failure reason");
    }

    @Test
    void unknownApplyIdReturnsNullAndTransitionsAreNoOps() {
        final SchemaApplyCoordinator coord = newCoordinator();
        assertNull(coord.get("does-not-exist"));
        assertDoesNotThrow(() -> coord.transition("does-not-exist", ApplyPhase.DDL));
        assertDoesNotThrow(() -> coord.markFailed("does-not-exist", "x"));
    }

    @Test
    void getLatestByFileHonorsExpectedContentHash() {
        final SchemaApplyCoordinator coord = newCoordinator();
        coord.begin("otel-rules", "vm", "hash-A");

        // Matching hash resolves; a different hash (caller asks about content the latest apply is
        // NOT for) returns null so the caller doesn't misreport an unrelated apply.
        assertNotNull(coord.getLatestByFile("otel-rules", "vm", "hash-A"));
        assertNull(coord.getLatestByFile("otel-rules", "vm", "hash-B"));
        assertNull(coord.getLatestByFile("otel-rules", "absent", null));
    }

    @Test
    void latestByFileFollowsTheNewestApply() {
        final SchemaApplyCoordinator coord = newCoordinator();
        coord.begin("otel-rules", "vm", "hash-old");
        final String newer = coord.begin("otel-rules", "vm", "hash-new");
        final ApplyStatus latest = coord.getLatestByFile("otel-rules", "vm", null);
        assertEquals(newer, latest.getApplyId());
        assertEquals("hash-new", latest.getContentHash());
        assertEquals(2, coord.trackedCount(), "both applies remain tracked until eviction");
    }
}
