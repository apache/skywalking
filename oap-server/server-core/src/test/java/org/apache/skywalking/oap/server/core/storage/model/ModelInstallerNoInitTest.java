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

import java.time.Duration;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the runtime-rule (DSL) schema-change path on a {@code no-init} OAP —
 * every production cluster node runs no-init. The base {@link ModelInstaller#whenCreating}
 * poll loop must defer to the init OAP only for the static boot-time opt
 * ({@link StorageManipulationOpt#schemaCreateIfAbsent()}); a runtime-rule
 * {@link StorageManipulationOpt#withSchemaChange()} apply must fall through to
 * {@code createTable} and create the resource itself, because no init OAP knows about a
 * metric created at runtime. Before the {@code deferDDLToInitNode} flag, a no-init OAP
 * routed the runtime create into the poll loop and blocked forever.
 */
class ModelInstallerNoInitTest {

    @AfterEach
    void resetRunningMode() {
        // RunningMode is a process-wide static; setMode("") is a no-op, so reset to a
        // neutral non-init/non-no-init value to avoid leaking no-init into other tests.
        RunningMode.setMode("default");
    }

    @Test
    void deferFlagSetOnlyOnStaticBootOpt() {
        assertTrue(StorageManipulationOpt.schemaCreateIfAbsent().getFlags().isDeferDDLToInitNode(),
            "static boot opt must defer DDL to the init node");
        assertFalse(StorageManipulationOpt.withSchemaChange().getFlags().isDeferDDLToInitNode(),
            "runtime-rule withSchemaChange must NOT defer — it is the DDL authority");
        assertFalse(StorageManipulationOpt.verifySchemaOnly().getFlags().isDeferDDLToInitNode());
        assertFalse(StorageManipulationOpt.withoutSchemaChange().getFlags().isDeferDDLToInitNode());
    }

    @Test
    void noInitMainCreatesNewMetricUnderWithSchemaChange() {
        RunningMode.setMode("no-init");
        final RecordingInstaller installer = new RecordingInstaller(false /* resource absent */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("runtime_metric");

        // Must return (not spin in the no-init poll loop) and must create the resource. The
        // preemptive timeout turns a regression — the historical infinite wait — into a fast
        // failure instead of a hung build.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
            installer.whenCreating(model, StorageManipulationOpt.withSchemaChange()));
        assertEquals(1, installer.createTableCalls,
            "runtime withSchemaChange on a no-init OAP must create the new resource");
    }

    @Test
    void noInitStaticBootDefersToInitNode() throws StorageException {
        RunningMode.setMode("no-init");
        // Resource already present so the defer poll loop breaks on its first probe instead
        // of waiting forever — lets the test assert the defer path without hanging.
        final RecordingInstaller installer = new RecordingInstaller(true /* resource present */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("static_metric");

        installer.whenCreating(model, StorageManipulationOpt.schemaCreateIfAbsent());
        assertEquals(0, installer.createTableCalls,
            "static boot on a no-init OAP must defer to the init node, never create");
    }

    @Test
    void withSchemaChangeSkipsCreateWhenResourceAlreadyExists() throws StorageException {
        RunningMode.setMode("no-init");
        final RecordingInstaller installer = new RecordingInstaller(true /* resource present */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("existing_metric");

        installer.whenCreating(model, StorageManipulationOpt.withSchemaChange());
        assertEquals(0, installer.createTableCalls,
            "withSchemaChange must not re-create a resource that already exists");
    }

    @Test
    void noInitDeferLoopRetriesTransientProbeErrorInsteadOfCrashing() {
        RunningMode.setMode("no-init");
        // The first existence probe throws a transient StorageException (mimicking a BanyanDB
        // cluster data node still Init-ing); the next probe reports the resource present.
        final RecordingInstaller installer = new RecordingInstaller(true /* present after transient */,
            1 /* one transient probe failure */, true /* retryable probe failure */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("static_metric_transient");

        // Must NOT propagate the transient (which would escape whenCreating and crash-loop the pod);
        // must retry in-loop, then return on the defer path without creating. 10s covers the 3s sleep.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
            installer.whenCreating(model, StorageManipulationOpt.schemaCreateIfAbsent()));
        assertEquals(0, installer.createTableCalls,
            "a transient probe error must be retried, then defer to the init node without creating");
        assertTrue(installer.probeCalls >= 2,
            "the loop must probe again after the transient instead of escaping on the first throw");
    }

    @Test
    void noInitDeferLoopPropagatesNonRetryableProbeError() {
        RunningMode.setMode("no-init");
        final RecordingInstaller installer = new RecordingInstaller(true /* unused */,
            1 /* one probe failure */, false /* permanent/non-retryable */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("static_metric_bad_model");

        assertThrows(StorageException.class,
            () -> installer.whenCreating(model, StorageManipulationOpt.schemaCreateIfAbsent()),
            "permanent model/config probe failures must not be converted into an infinite no-init wait");
        assertEquals(1, installer.probeCalls,
            "a non-retryable failure must escape without sleeping and probing again");
        assertEquals(0, installer.createTableCalls);
    }

    @Test
    void noInitDeferLoopPropagatesInterruptedSleep() {
        RunningMode.setMode("no-init");
        final RecordingInstaller installer = new RecordingInstaller(false /* resource absent */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("static_metric_wait_interrupted");

        Thread.currentThread().interrupt();
        try {
            assertThrows(StorageException.class,
                () -> installer.whenCreating(model, StorageManipulationOpt.schemaCreateIfAbsent()),
                "an interrupted no-init wait must fail fast so shutdown can proceed");
            assertTrue(Thread.currentThread().isInterrupted(),
                "the interrupt flag must be restored for upstream shutdown handling");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void withoutSchemaChangePopulatesLocalCacheAndIssuesNoBackendRpc() throws StorageException {
        // Peer reconciler tick (inspectBackend=false): the installer must refresh the local
        // schema cache (so a reshape re-add overwrites a now-stale entry) WITHOUT any backend
        // existence probe or create. This is the C-1 fix for the worker-without-cache /
        // stale-cache desync.
        final RecordingInstaller installer = new RecordingInstaller(false /* unused */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("runtime_metric_peer");

        installer.whenCreating(model, StorageManipulationOpt.withoutSchemaChange());

        assertEquals(1, installer.populateLocalCacheCalls,
            "a withoutSchemaChange (peer) apply must refresh the local schema cache");
        assertEquals(0, installer.probeCalls,
            "a withoutSchemaChange (peer) apply must issue zero backend existence probes");
        assertEquals(0, installer.createTableCalls,
            "a withoutSchemaChange (peer) apply must never create backend resources");
    }

    @Test
    void whenRemovingPeerEvictsLocalCacheWithoutDroppingBackend() throws StorageException {
        // Peer remove (dropOnRemoval=false): the backend drop is the main's job, but the peer
        // must still evict its own cache entry so a removed model leaves no stale translation.
        final RecordingInstaller installer = new RecordingInstaller(true /* unused */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("runtime_metric_remove_peer");

        installer.whenRemoving(model, StorageManipulationOpt.withoutSchemaChange());

        assertEquals(1, installer.evictLocalCacheCalls,
            "a peer remove must evict the local schema cache entry");
        assertEquals(0, installer.dropTableCalls,
            "a peer remove (dropOnRemoval off) must not drop backend resources");
    }

    @Test
    void whenRemovingMainDropsBackendThenEvictsLocalCache() throws StorageException {
        // Main remove (withSchemaChange, dropOnRemoval=true): drop the backend AND evict the
        // local cache so the insert-only registry does not keep a tombstoned model's entry.
        final RecordingInstaller installer = new RecordingInstaller(true /* unused */);
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("runtime_metric_remove_main");

        installer.whenRemoving(model, StorageManipulationOpt.withSchemaChange());

        assertEquals(1, installer.dropTableCalls,
            "a main remove must drop the backend resource");
        assertEquals(1, installer.evictLocalCacheCalls,
            "a main remove must evict the local schema cache entry after the drop");
    }

    /** Minimal concrete {@link ModelInstaller} that records createTable calls and reports a
     *  fixed existence result, so the base whenCreating branching can be exercised without a
     *  real storage backend. Optionally throws a transient {@link StorageException} on the first
     *  {@code transientProbeFailures} existence probes to exercise the no-init defer-loop retry. */
    private static final class RecordingInstaller extends ModelInstaller {
        private final boolean resourcePresent;
        private final int transientProbeFailures;
        private final boolean retryableProbeFailure;
        private int probeCalls;
        private int createTableCalls;
        private int populateLocalCacheCalls;
        private int evictLocalCacheCalls;
        private int dropTableCalls;

        private RecordingInstaller(final boolean resourcePresent) {
            this(resourcePresent, 0, false);
        }

        private RecordingInstaller(final boolean resourcePresent, final int transientProbeFailures) {
            this(resourcePresent, transientProbeFailures, true);
        }

        private RecordingInstaller(final boolean resourcePresent, final int transientProbeFailures,
                                   final boolean retryableProbeFailure) {
            super(null, null);
            this.resourcePresent = resourcePresent;
            this.transientProbeFailures = transientProbeFailures;
            this.retryableProbeFailure = retryableProbeFailure;
        }

        @Override
        public InstallInfo isExists(final Model model, final StorageManipulationOpt opt) throws StorageException {
            if (probeCalls++ < transientProbeFailures) {
                throw new StorageException("transient backend error");
            }
            final TestInstallInfo info = new TestInstallInfo(model);
            info.setAllExist(resourcePresent);
            return info;
        }

        @Override
        protected boolean isRetryableNoInitProbeFailure(final StorageException e) {
            return retryableProbeFailure;
        }

        @Override
        public void createTable(final Model model) {
            createTableCalls++;
        }

        @Override
        public void dropTable(final Model model) {
            dropTableCalls++;
        }

        @Override
        protected void populateLocalCacheOnly(final Model model, final StorageManipulationOpt opt) {
            populateLocalCacheCalls++;
        }

        @Override
        protected void evictLocalCache(final Model model) {
            evictLocalCacheCalls++;
        }
    }

    private static final class TestInstallInfo extends ModelInstaller.InstallInfo {
        private TestInstallInfo(final Model model) {
            super(model);
        }

        @Override
        public String buildInstallInfoMsg() {
            return "test";
        }
    }
}
