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

package org.apache.skywalking.oap.server.core.dsldebug;

import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GateHolderTest {

    @Test
    public void newHolder_idle() {
        final GateHolder h = new GateHolder("hash-1");
        assertFalse(h.isGateOn(), "fresh holder must start with gate=false (idle, no recorders)");
        assertEquals(0, h.getRecorders().length);
        assertEquals("hash-1", h.getContent());
    }

    @Test
    public void addRecorder_zeroToOne_flipsGateOn() {
        final GateHolder h = new GateHolder("hash-1");
        final DebugRecorder a = recorder("session-a");

        h.addRecorder(a);

        assertTrue(h.isGateOn(), "0->1 transition must flip gate=true so probes start firing");
        assertEquals(1, h.getRecorders().length);
        assertSame(a, h.getRecorders()[0]);
    }

    @Test
    public void addRecorder_secondInsert_keepsGateOnAndCowSnapshots() {
        final GateHolder h = new GateHolder("hash-1");
        final DebugRecorder a = recorder("session-a");
        final DebugRecorder b = recorder("session-b");

        h.addRecorder(a);
        final DebugRecorder[] snapshotAfterA = h.getRecorders();
        h.addRecorder(b);

        assertTrue(h.isGateOn(), "second add must leave gate=true");
        assertEquals(2, h.getRecorders().length);
        assertNotSame(snapshotAfterA, h.getRecorders(), "writers must publish a new array (CoW), not mutate in place");
        assertSame(a, h.getRecorders()[0]);
        assertSame(b, h.getRecorders()[1]);
    }

    @Test
    public void removeRecorder_lastOne_flipsGateOff() {
        final GateHolder h = new GateHolder("hash-1");
        final DebugRecorder a = recorder("session-a");
        h.addRecorder(a);

        h.removeRecorder(a);

        assertFalse(h.isGateOn(), "1->0 transition must flip gate=false so probes stop firing (idle path)");
        assertEquals(0, h.getRecorders().length);
    }

    @Test
    public void removeRecorder_oneOfMany_keepsGateOn() {
        final GateHolder h = new GateHolder("hash-1");
        final DebugRecorder a = recorder("session-a");
        final DebugRecorder b = recorder("session-b");
        final DebugRecorder c = recorder("session-c");
        h.addRecorder(a);
        h.addRecorder(b);
        h.addRecorder(c);

        h.removeRecorder(b);

        assertTrue(h.isGateOn(), "removing a non-last recorder must keep gate=true");
        assertEquals(2, h.getRecorders().length);
        assertSame(a, h.getRecorders()[0]);
        assertSame(c, h.getRecorders()[1]);
    }

    @Test
    public void removeRecorder_unknown_isIdempotent() {
        // Idempotent uninstall — useful when retention timeout races against an explicit
        // stop. Removing an unbound recorder must not throw and must not change state.
        final GateHolder h = new GateHolder("hash-1");
        final DebugRecorder a = recorder("session-a");
        final DebugRecorder ghost = recorder("ghost");
        h.addRecorder(a);
        final DebugRecorder[] before = h.getRecorders();
        final boolean gateBefore = h.isGateOn();

        h.removeRecorder(ghost);

        assertSame(before, h.getRecorders(), "unknown recorder removal must not allocate a new snapshot");
        assertEquals(gateBefore, h.isGateOn());
    }

    @Test
    public void content_nullCoercedToEmpty() {
        // GateHolder accepts null content (mock holders in tests, paths that don't
        // wire raw text) and stores "" — captures still emit a content field, just
        // empty. No NPE on construction.
        final GateHolder h = new GateHolder(null);
        assertEquals("", h.getContent());
    }

    @Test
    public void addRecorder_rejectsNull() {
        final GateHolder h = new GateHolder("hash-1");
        assertThrows(NullPointerException.class, () -> h.addRecorder(null));
    }

    private static DebugRecorder recorder(final String sessionId) {
        return new TestRecorder(sessionId);
    }

    private static final class TestRecorder implements DebugRecorder {
        private final String sessionId;

        private TestRecorder(final String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public RuleKey ruleKey() {
            return new RuleKey(Catalog.OTEL_RULES, "test", "test");
        }

        @Override
        public boolean matches(final RuleKey candidate) {
            return false;
        }

        @Override
        public boolean isCaptured() {
            return false;
        }
    }
}
