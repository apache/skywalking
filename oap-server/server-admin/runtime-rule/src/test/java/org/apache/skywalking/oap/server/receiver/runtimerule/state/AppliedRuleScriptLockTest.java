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

package org.apache.skywalking.oap.server.receiver.runtimerule.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lazy lock semantics on the unified {@code rules} map via
 * {@link AppliedRuleScript#lockFor}. The lock used to live in a dedicated
 * {@code PerFileLockMap}; it now lives on each {@link AppliedRuleScript}, lazy-created on
 * first {@code lockFor} call. The contract this test enforces is unchanged: same key →
 * same lock instance; different keys → independent locks; the lock is a real mutex.
 */
class AppliedRuleScriptLockTest {

    @Test
    void sameKeyReturnsSameLock() {
        // Apply-path correctness depends on a single mutex per file — if two concurrent
        // dslManager ticks for the same rule got different ReentrantLock instances, the
        // compile+swap sequence would not actually be serialized.
        final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
        final ReentrantLock a = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        final ReentrantLock b = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        assertSame(a, b);
    }

    @Test
    void differentFilesGetIndependentLocks() {
        final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
        final ReentrantLock a = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        final ReentrantLock b = AppliedRuleScript.lockFor(rules, "mal", "k8s.yaml");
        assertNotSame(a, b);
    }

    @Test
    void differentCatalogsWithSameNameGetIndependentLocks() {
        // A MAL file and a LAL file named "demo" are distinct bundles and must not share a lock.
        final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
        final ReentrantLock a = AppliedRuleScript.lockFor(rules, "mal", "demo");
        final ReentrantLock b = AppliedRuleScript.lockFor(rules, "lal", "demo");
        assertNotSame(a, b);
    }

    @Test
    void lockActuallyBlocksAcrossThreads() throws Exception {
        // Sanity: the returned ReentrantLock is a real mutex (not a no-op). Caller holds it on
        // one thread, second thread's tryLock must observe the locked state.
        final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
        final ReentrantLock lock = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        lock.lock();
        try {
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] acquired = {true};
            final Thread t = new Thread(() -> {
                try {
                    acquired[0] = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml")
                        .tryLock(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.start();
            assertTrue(done.await(2, TimeUnit.SECONDS), "probing thread should finish");
            assertFalse(acquired[0], "second thread must not acquire a held lock");
        } finally {
            lock.unlock();
        }
    }

    @Test
    void lockSurvivesWithStateBuilders() {
        // A with* builder produces a new AppliedRuleScript; the lock must remain stable so
        // a thread that acquired the lock on the prior instance and released the map slot
        // mid-update still owns the same mutex when it unlocks. This is the invariant that
        // makes consolidating snapshot+content+lock+applied into one AppliedRuleScript safe.
        final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
        final ReentrantLock first = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        rules.compute("mal:vm.yaml", (k, prev) -> prev.withContent("body"));
        final ReentrantLock second = AppliedRuleScript.lockFor(rules, "mal", "vm.yaml");
        assertSame(first, second,
            "lock identity must survive with* state replacements on the same key");
    }
}
