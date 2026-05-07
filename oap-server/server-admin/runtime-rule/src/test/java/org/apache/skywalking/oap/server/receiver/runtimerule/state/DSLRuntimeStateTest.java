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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DSLRuntimeStateTest {

    @Test
    void runningFactoryProducesLiveState() {
        final DSLRuntimeState s = DSLRuntimeState.running("mal", "vm.yaml", "abc123", 1000L);
        assertEquals("mal", s.getCatalog());
        assertEquals("vm.yaml", s.getName());
        assertEquals("abc123", s.getContentHash());
        assertEquals(DSLRuntimeState.LocalState.RUNNING, s.getLocalState());
        assertEquals(DSLRuntimeState.LoaderGc.LIVE, s.getLoaderGc());
        assertNull(s.getLastApplyError());
        assertEquals(1000L, s.getLastAppliedAtMs());
        assertEquals(1000L, s.getEnteredCurrentStateAtMs());
    }

    @Test
    void withLocalStateReturnsNewInstanceOnChange() {
        final DSLRuntimeState s1 = DSLRuntimeState.running("mal", "vm.yaml", "abc", 1000L);
        final DSLRuntimeState s2 = s1.withLocalState(DSLRuntimeState.LocalState.SUSPENDED, 2000L);
        assertNotSame(s1, s2);
        // Original snapshot intact — readers that captured s1 never observe s2's mutation.
        assertEquals(DSLRuntimeState.LocalState.RUNNING, s1.getLocalState());
        assertEquals(DSLRuntimeState.LocalState.SUSPENDED, s2.getLocalState());
        // Entering a state stamps the transition time; lastAppliedAtMs unchanged (still the
        // last *successful apply*, not the last state transition).
        assertEquals(2000L, s2.getEnteredCurrentStateAtMs());
        assertEquals(1000L, s2.getLastAppliedAtMs());
    }

    @Test
    void withLocalStateIsIdentityOnSameState() {
        // Same-value withers must short-circuit — the dslManager calls these unconditionally on
        // every tick and allocating a new DSLRuntimeState per no-op would thrash the state map.
        final DSLRuntimeState s1 = DSLRuntimeState.running("mal", "vm.yaml", "abc", 1000L);
        final DSLRuntimeState s2 = s1.withLocalState(DSLRuntimeState.LocalState.RUNNING, 9999L);
        assertSame(s1, s2);
    }

    @Test
    void withLoaderGcTransitionsPendingThenCollected() {
        final DSLRuntimeState live = DSLRuntimeState.running("mal", "vm.yaml", "abc", 1000L);
        final DSLRuntimeState pending = live.withLoaderGc(DSLRuntimeState.LoaderGc.PENDING);
        final DSLRuntimeState collected = pending.withLoaderGc(DSLRuntimeState.LoaderGc.COLLECTED);
        assertEquals(DSLRuntimeState.LoaderGc.LIVE, live.getLoaderGc());
        assertEquals(DSLRuntimeState.LoaderGc.PENDING, pending.getLoaderGc());
        assertEquals(DSLRuntimeState.LoaderGc.COLLECTED, collected.getLoaderGc());
    }

    @Test
    void withApplyErrorStampsTimestampAndMessage() {
        final DSLRuntimeState s1 = DSLRuntimeState.running("mal", "vm.yaml", "abc", 1000L);
        final DSLRuntimeState s2 = s1.withApplyError("compile failed", 5000L);
        assertEquals("compile failed", s2.getLastApplyError());
        assertEquals(5000L, s2.getLastAppliedAtMs());
        // enteredCurrentStateAtMs is not advanced: an error does not change local state, just
        // annotates the outcome of the most recent apply attempt.
        assertEquals(1000L, s2.getEnteredCurrentStateAtMs());
    }

    @Test
    void withContentHashRefreshesAppliedAndEntered() {
        final DSLRuntimeState s1 = DSLRuntimeState.running("mal", "vm.yaml", "old", 1000L);
        final DSLRuntimeState s2 = s1.withContentHash("new", 7000L);
        assertEquals("new", s2.getContentHash());
        // A new content hash is always a successful re-apply, so both timestamps advance.
        assertEquals(7000L, s2.getLastAppliedAtMs());
        assertEquals(7000L, s2.getEnteredCurrentStateAtMs());
    }

    @Test
    void withContentHashIsIdentityOnSameHash() {
        final DSLRuntimeState s1 = DSLRuntimeState.running("mal", "vm.yaml", "abc", 1000L);
        final DSLRuntimeState s2 = s1.withContentHash("abc", 9999L);
        assertSame(s1, s2);
    }
}
