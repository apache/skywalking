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

package org.apache.skywalking.oap.server.core.classloader;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Singleton lifecycle tests for {@link DSLClassLoaderManager}. Each test uses a unique rule
 * name so concurrent test execution doesn't collide on the shared singleton state.
 */
class DSLClassLoaderManagerTest {

    @Test
    void newBuilderDoesNotInstallUntilCommit() {
        // The split between newBuilder (mint) and commit (promote-to-active) exists so a
        // failed compile cannot displace the live loader. Confirm the contract: after
        // newBuilder alone, active() is still empty for this key.
        final String rule = "build-no-install-" + System.nanoTime();
        final RuleClassLoader fresh = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.OTEL_RULES, rule, DSLClassLoaderManager.Kind.RUNTIME, "h1");
        assertFalse(DSLClassLoaderManager.INSTANCE.active(Catalog.OTEL_RULES, rule).isPresent(),
            "newBuilder must not install the loader as active");
        DSLClassLoaderManager.INSTANCE.commit(fresh);
        assertSame(fresh, DSLClassLoaderManager.INSTANCE.active(Catalog.OTEL_RULES, rule).get());

        DSLClassLoaderManager.INSTANCE.dropRuntime(Catalog.OTEL_RULES, rule);
    }

    @Test
    void commitReplacesPriorAndReturnsIt() {
        final String rule = "commit-replace-" + System.nanoTime();
        final RuleClassLoader first = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.OTEL_RULES, rule, DSLClassLoaderManager.Kind.RUNTIME, "h1");
        DSLClassLoaderManager.INSTANCE.commit(first);

        final RuleClassLoader second = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.OTEL_RULES, rule, DSLClassLoaderManager.Kind.RUNTIME, "h2");
        final Optional<RuleClassLoader> displaced = DSLClassLoaderManager.INSTANCE.commit(second);
        assertTrue(displaced.isPresent());
        assertSame(first, displaced.get(), "commit must return the prior loader for retire decisions");
        assertSame(second, DSLClassLoaderManager.INSTANCE.active(Catalog.OTEL_RULES, rule).get());

        DSLClassLoaderManager.INSTANCE.dropRuntime(Catalog.OTEL_RULES, rule);
    }

    @Test
    void dropRuntimeReturnsActiveAndClearsEntry() {
        final String rule = "drop-runtime-" + System.nanoTime();
        final RuleClassLoader loader = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.LAL, rule, DSLClassLoaderManager.Kind.RUNTIME, "h");
        DSLClassLoaderManager.INSTANCE.commit(loader);

        final Optional<RuleClassLoader> dropped = DSLClassLoaderManager.INSTANCE.dropRuntime(
            Catalog.LAL, rule);
        assertTrue(dropped.isPresent());
        assertFalse(DSLClassLoaderManager.INSTANCE.active(Catalog.LAL, rule).isPresent());
    }

    @Test
    void dropRuntimeOnAbsentKeyReturnsEmpty() {
        final String rule = "drop-absent-" + System.nanoTime();
        assertFalse(DSLClassLoaderManager.INSTANCE.dropRuntime(Catalog.LAL, rule).isPresent());
    }

    @Test
    void retireGraveyardsAnExternallyHeldLoader() {
        final String rule = "retire-external-" + System.nanoTime();
        final RuleClassLoader loader = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.LOG_MAL_RULES, rule, DSLClassLoaderManager.Kind.RUNTIME, "h");
        final int before = DSLClassLoaderManager.INSTANCE.pendingCount();
        DSLClassLoaderManager.INSTANCE.retire(loader);
        assertEquals(before + 1, DSLClassLoaderManager.INSTANCE.pendingCount(),
            "retire should move the loader into the graveyard's pending set");

        // Strong ref retained for the duration of the test so phantom can't enqueue —
        // pendingCount must stay elevated relative to the pre-test reading.
        assertSame(Catalog.LOG_MAL_RULES, loader.getCatalog());
    }

    @Test
    void loaderNameKindPrefixIsConsistentWithBuildKind() {
        final String rule = "kind-prefix-" + System.nanoTime();
        final RuleClassLoader runtimeLoader = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.LAL, rule, DSLClassLoaderManager.Kind.RUNTIME, "h");
        assertTrue(runtimeLoader.getName().startsWith("runtime-rule:lal/" + rule));

        final RuleClassLoader staticLoader = DSLClassLoaderManager.INSTANCE.newBuilder(
            Catalog.LAL, rule, DSLClassLoaderManager.Kind.STATIC, "h");
        assertTrue(staticLoader.getName().startsWith("static:lal/" + rule));
    }
}
