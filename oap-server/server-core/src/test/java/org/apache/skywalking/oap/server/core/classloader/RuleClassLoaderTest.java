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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleClassLoaderTest {

    @Test
    void fieldsAreExposedForGraveyardAccounting() {
        // The graveyard captures (kind, catalog, rule, contentHash) at retire() time — the
        // loader must surface them exactly as constructed so operators can map phantom
        // enqueues back to the YAML file that produced the classes.
        final ClassLoader parent = Thread.currentThread().getContextClassLoader();
        final RuleClassLoader loader = new RuleClassLoader(
            DSLClassLoaderManager.Kind.RUNTIME, Catalog.OTEL_RULES, "vm.yaml",
            "deadbeef01234567", parent);
        assertEquals(DSLClassLoaderManager.Kind.RUNTIME, loader.getKind());
        assertEquals(Catalog.OTEL_RULES, loader.getCatalog());
        assertEquals("vm.yaml", loader.getRule());
        assertEquals("deadbeef01234567", loader.getContentHash());
    }

    @Test
    void runtimeKindLoaderNameHasRuntimeRulePrefix() {
        // Loader-name format is observable on every log line that prints the loader; the
        // prefix must distinguish a runtime override from a static fall-over at a glance.
        final RuleClassLoader loader = new RuleClassLoader(
            DSLClassLoaderManager.Kind.RUNTIME, Catalog.LAL, "default", "h",
            Thread.currentThread().getContextClassLoader());
        assertTrue(loader.getName().startsWith("runtime-rule:lal/default@"),
            "expected runtime-rule prefix, got: " + loader.getName());
    }

    @Test
    void bundledKindLoaderNameHasBundledPrefix() {
        final RuleClassLoader loader = new RuleClassLoader(
            DSLClassLoaderManager.Kind.BUNDLED, Catalog.LOG_MAL_RULES, "service-resp", "h",
            Thread.currentThread().getContextClassLoader());
        assertTrue(loader.getName().startsWith("bundled:log-mal-rules/service-resp@"),
            "expected bundled prefix, got: " + loader.getName());
    }

    @Test
    void nullHashIsAcceptedWithoutNpe() {
        final RuleClassLoader loader = new RuleClassLoader(
            DSLClassLoaderManager.Kind.RUNTIME, Catalog.OTEL_RULES, "bad.yaml", null,
            Thread.currentThread().getContextClassLoader());
        assertEquals(Catalog.OTEL_RULES, loader.getCatalog());
        assertEquals("bad.yaml", loader.getRule());
        org.junit.jupiter.api.Assertions.assertNull(loader.getContentHash());
    }

    @Test
    void parentDelegationResolvesParentClasses() throws Exception {
        // The loader is parented to the app loader so shipped classes resolve via parent-first
        // lookup. A concrete OAP class that's always on the classpath confirms that contract.
        final ClassLoader parent = Thread.currentThread().getContextClassLoader();
        final RuleClassLoader loader = new RuleClassLoader(
            DSLClassLoaderManager.Kind.RUNTIME, Catalog.LAL, "vm", "h", parent);
        final Class<?> k = loader.loadClass("org.apache.skywalking.oap.server.core.CoreModule");
        assertNotNull(k);
    }
}
