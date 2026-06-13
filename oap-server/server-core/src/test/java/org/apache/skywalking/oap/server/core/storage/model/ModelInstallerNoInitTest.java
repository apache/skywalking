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

    /** Minimal concrete {@link ModelInstaller} that records createTable calls and reports a
     *  fixed existence result, so the base whenCreating branching can be exercised without a
     *  real storage backend. */
    private static final class RecordingInstaller extends ModelInstaller {
        private final boolean resourcePresent;
        private int createTableCalls;

        private RecordingInstaller(final boolean resourcePresent) {
            super(null, null);
            this.resourcePresent = resourcePresent;
        }

        @Override
        public InstallInfo isExists(final Model model, final StorageManipulationOpt opt) {
            final TestInstallInfo info = new TestInstallInfo(model);
            info.setAllExist(resourcePresent);
            return info;
        }

        @Override
        public void createTable(final Model model) {
            createTableCalls++;
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
