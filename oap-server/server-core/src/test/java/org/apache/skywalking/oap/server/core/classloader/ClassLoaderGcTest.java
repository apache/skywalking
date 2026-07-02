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

import java.lang.ref.Reference;
import java.util.Collection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence-gating tests for {@link ClassLoaderGc}. A retired loader must never be flagged as
 * a leak on wall-clock age alone — only after a class-unloading GC cycle demonstrably ran
 * past the settle window and the loader survived it. Each test uses its own {@code
 * ClassLoaderGc} instance, so nothing here touches the {@link DSLClassLoaderManager}
 * singleton's graveyard.
 *
 * <p>Deliberately no real-GC test here: asserting that the unload probe is collected by
 * {@code System.gc()} depends on collector behavior (G1/ZGC/Serial timing, {@code
 * -XX:+DisableExplicitGC}) and would be a flake source in CI. That physical behavior was
 * verified manually across collectors when the probe was introduced; these tests pin the
 * gating logic with injected evidence instead, which is collector-independent.
 */
class ClassLoaderGcTest {

    private RuleClassLoader newLoader(final String rule) {
        return new RuleClassLoader(DSLClassLoaderManager.Kind.RUNTIME, Catalog.LAL, rule,
            "hash-" + rule, ClassLoaderGcTest.class.getClassLoader());
    }

    @Test
    void noSuspectWithoutUnloadEvidence() {
        final ClassLoaderGc gc = new ClassLoaderGc();
        final RuleClassLoader pinned = newLoader("no-evidence");
        gc.retire(pinned);
        // Even with a zero settle window and any wall-clock age, no completed class-unloading
        // cycle has been observed, so nothing may be flagged — this is the exact false-alarm
        // case (idle heap after a hot update).
        assertTrue(gc.leakSuspects(0).isEmpty(),
            "no class-unloading GC cycle observed — pending must not be treated as a leak");
        // JIT liveness analysis may end an object's reachability before its local variable
        // goes out of scope; the fence guarantees the loader stays "pinned" through the
        // assertion above regardless of compiler optimization.
        Reference.reachabilityFence(pinned);
    }

    @Test
    void suspectRequiresEvidencePastSettleWindow() {
        final ClassLoaderGc gc = new ClassLoaderGc();
        final RuleClassLoader pinned = newLoader("evidence-gated");
        gc.retire(pinned);
        // Derive timestamps from the entry itself so the test never reads the wall clock —
        // an NTP step between retire() and here would otherwise skew the window math.
        final long retiredAtMs = gc.pending().iterator().next().retiredAtMs();

        // A cycle that completed before the settle window elapsed doesn't qualify: transient
        // holders (the apply call chain) may legitimately have pinned the loader through it.
        gc.recordUnloadEvidence(retiredAtMs + 59_999L);
        assertTrue(gc.leakSuspects(60_000).isEmpty(),
            "evidence must postdate retirement by the full settle window");

        // A cycle confirmed at retirement + settle flags the entry — exactly once.
        gc.recordUnloadEvidence(retiredAtMs + 60_000L);
        final Collection<ClassLoaderGc.Retired> suspects = gc.leakSuspects(60_000);
        assertEquals(1, suspects.size());
        assertEquals("evidence-gated", suspects.iterator().next().rule());
        assertTrue(gc.leakSuspects(60_000).isEmpty(), "leak WARN latch is one-shot per loader");

        Reference.reachabilityFence(pinned);
    }

    @Test
    void recordUnloadEvidenceKeepsMaximum() {
        final ClassLoaderGc gc = new ClassLoaderGc();
        gc.recordUnloadEvidence(100L);
        gc.recordUnloadEvidence(50L);
        assertEquals(100L, gc.unloadEvidenceUpToMs(), "older evidence must not regress the watermark");
    }
}
