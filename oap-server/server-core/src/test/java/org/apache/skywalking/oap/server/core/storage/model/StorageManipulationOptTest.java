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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the batched-fence machinery on {@link StorageManipulationOpt}: a
 * {@link StorageManipulationOpt#withSchemaChangeDeferredFence()} opt carries the same flags as
 * {@link StorageManipulationOpt#withSchemaChange()} but lets the installer register a single
 * {@link StorageManipulationOpt.DeferredFence} that the apply orchestration runs ONCE instead of
 * one fence per metric/downsampling.
 */
class StorageManipulationOptTest {

    @Test
    void deferredFenceOptHasSameFlagsAsWithSchemaChange() {
        final StorageManipulationOpt deferred = StorageManipulationOpt.withSchemaChangeDeferredFence();
        final StorageManipulationOpt plain = StorageManipulationOpt.withSchemaChange();
        // Behaviour must be identical except for the batching toggle — same mode/flags so every
        // create/update/drop privilege the installer checks is unchanged.
        assertEquals(plain.getMode(), deferred.getMode(),
            "deferred-fence opt must keep the WITH_SCHEMA_CHANGE mode");
        assertTrue(deferred.isWithSchemaChange(),
            "deferred-fence opt must still report withSchemaChange semantics");
        assertTrue(deferred.isDeferFence(),
            "deferred-fence opt must flag deferFence");
        assertFalse(plain.isDeferFence(),
            "the plain withSchemaChange opt must NOT defer the fence");
        assertFalse(StorageManipulationOpt.withoutSchemaChange().isDeferFence());
    }

    @Test
    void runDeferredFenceInvokesRegisteredFenceOnce() throws StorageException {
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        final AtomicInteger calls = new AtomicInteger();
        opt.setDeferredFence(calls::incrementAndGet);

        opt.runDeferredFence();

        assertEquals(1, calls.get(), "the registered fence must run exactly once on flush");
    }

    @Test
    void runDeferredFenceIsNoOpWhenNothingRegistered() {
        // Peer / no-change applies (and non-BanyanDB backends) never register a fence; flushing
        // must be a safe no-op, not an NPE.
        assertDoesNotThrow(() -> StorageManipulationOpt.withSchemaChangeDeferredFence().runDeferredFence());
        assertDoesNotThrow(() -> StorageManipulationOpt.withoutSchemaChange().runDeferredFence());
    }

    @Test
    void runDeferredFencePropagatesStorageException() {
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        opt.setDeferredFence(() -> {
            throw new StorageException("barrier transport error");
        });
        // A barrier transport error must surface so the apply aborts exactly like an inline fence.
        assertThrows(StorageException.class, opt::runDeferredFence);
    }

    @Test
    void runDeferredFenceIsOneShotAcrossFiles() throws StorageException {
        // A reconciler tick reuses ONE opt across every rule file. After a file flushes its
        // fence, a later file that performed no DDL (registers no new closure) must NOT re-run
        // the earlier file's stale fence.
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        final AtomicInteger calls = new AtomicInteger();
        opt.setDeferredFence(calls::incrementAndGet);

        opt.runDeferredFence();
        opt.runDeferredFence();

        assertEquals(1, calls.get(), "the fence must run once and be cleared, not re-run for the next file");
    }

    @Test
    void runDeferredFenceResetsRevisionAfterAwait() throws StorageException {
        // The closure must observe this file's accumulated revision DURING await, then the opt
        // resets so the next file fences on its own DDL only — not the cumulative max.
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        opt.recordModRevision(42L);
        final AtomicInteger seenDuringAwait = new AtomicInteger();
        opt.setDeferredFence(() -> seenDuringAwait.set((int) opt.getMaxModRevision()));

        opt.runDeferredFence();

        assertEquals(42L, seenDuringAwait.get(), "the fence must see the recorded revision during await");
        assertEquals(StorageManipulationOpt.DEFAULT_MOD_REVISION, opt.getMaxModRevision(),
            "the revision must reset after the fence so a later file is not over-fenced");
    }

    @Test
    void runDeferredFenceResetsRevisionEvenWithNoClosureRegistered() {
        // A no-DDL file on a shared tick opt registers no closure, but a prior file (or its
        // commit-tail drops) may have left a revision on the opt. runDeferredFence must still clear
        // it so the next file is not over-fenced on a stale revision.
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        opt.recordModRevision(77L);

        assertDoesNotThrow(opt::runDeferredFence);

        assertEquals(StorageManipulationOpt.DEFAULT_MOD_REVISION, opt.getMaxModRevision(),
            "a no-closure flush must still reset the accumulated revision (shared-tick isolation)");
    }

    @Test
    void runDeferredFenceResetsRevisionEvenWhenFenceThrows() {
        // A barrier transport failure on one file must not leave a stale revision that the next
        // file would inherit; the reset runs in finally.
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        opt.recordModRevision(99L);
        opt.setDeferredFence(() -> {
            throw new StorageException("barrier transport error");
        });

        assertThrows(StorageException.class, opt::runDeferredFence);
        assertEquals(StorageManipulationOpt.DEFAULT_MOD_REVISION, opt.getMaxModRevision(),
            "the revision must reset even when the fence throws");
    }

    @Test
    void laterSetDeferredFenceWins() throws StorageException {
        // The installer may register the closure once per resource; the latest (equivalent) one
        // wins and still runs a single time.
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        final AtomicInteger first = new AtomicInteger();
        final AtomicInteger second = new AtomicInteger();
        opt.setDeferredFence(first::incrementAndGet);
        opt.setDeferredFence(second::incrementAndGet);

        opt.runDeferredFence();

        assertEquals(0, first.get(), "an overwritten fence must not run");
        assertEquals(1, second.get(), "the latest registered fence runs once");
    }
}
