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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebugSessionRegistryTest {

    @Test
    public void install_unknownRule_returnsNull() {
        final DebugSessionRegistry registry = new DebugSessionRegistry();
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        assertNull(registry.install(key, "client-a", SessionLimits.DEFAULT));
    }

    @Test
    public void install_bindsRecorder_andFlipsHolderGate() {
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder holder = new GateHolder("hash-1");
        final DebugSessionRegistry registry = registryWith(holder, key);

        final DebugSession session = registry.install(key, "client-a", SessionLimits.DEFAULT);

        assertNotNull(session);
        assertEquals(key, session.getRuleKey());
        assertSame(holder, session.getBoundHolder());
        assertTrue(holder.isGateOn(), "0->1 transition must flip gate=true");
        assertEquals(1, holder.getRecorders().length);
    }

    @Test
    public void install_withoutFactory_throwsConfigError() {
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder holder = new GateHolder("hash-1");
        final DebugSessionRegistry registry = new DebugSessionRegistry();
        registry.registerLookup(staticLookup(key, holder));
        // No factory registered — install() must signal a wiring bug, not a 404.
        assertThrows(IllegalStateException.class,
                     () -> registry.install(key, "client-a", SessionLimits.DEFAULT));
    }

    @Test
    public void uninstall_removesRecorderFromBoundHolder() {
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder holder = new GateHolder("hash-1");
        final DebugSessionRegistry registry = registryWith(holder, key);
        final DebugSession session = registry.install(key, "client-a", SessionLimits.DEFAULT);

        assertTrue(registry.uninstall(session.getSessionId()));

        assertFalse(holder.isGateOn(), "1->0 transition must flip gate=false");
        assertEquals(0, holder.getRecorders().length);
        assertNull(registry.find(session.getSessionId()));
    }

    @Test
    public void uninstall_unknownId_isIdempotent() {
        final DebugSessionRegistry registry = new DebugSessionRegistry();
        assertFalse(registry.uninstall("never-existed"));
    }

    @Test
    public void uninstall_usesBoundHolder_evenAfterHotUpdate() {
        // SWIP §3.3: pre-update sessions must uninstall on the *original* holder so the
        // V1 binding drains correctly. Simulate a hot-update by handing the registry
        // a different holder for the same key after install — uninstall must still
        // walk the original.
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder originalHolder = new GateHolder("hash-1");
        final GateHolder rotatedHolder = new GateHolder("hash-2");

        final MutableLookup lookup = new MutableLookup();
        lookup.bind(key, originalHolder);

        final DebugSessionRegistry registry = new DebugSessionRegistry();
        registry.registerLookup(lookup);
        registry.registerRecorderFactory(new TrivialRecorderFactory());

        final DebugSession session = registry.install(key, "client-a", SessionLimits.DEFAULT);
        assertSame(originalHolder, session.getBoundHolder());
        assertTrue(originalHolder.isGateOn());

        // Hot-update: the registry now resolves to a *new* holder.
        lookup.bind(key, rotatedHolder);

        registry.uninstall(session.getSessionId());

        assertFalse(originalHolder.isGateOn(), "original holder must be torn down");
        assertEquals(0, originalHolder.getRecorders().length);
        assertFalse(rotatedHolder.isGateOn(), "rotated holder must NOT have been touched");
        assertEquals(0, rotatedHolder.getRecorders().length);
    }

    @Test
    public void installWithId_concurrentInstalls_neverExceedActiveSessionsCeiling() throws Exception {
        // SWIP §5: with the per-session byte cap intentionally removed, the active-
        // session ceiling is the load-bearing memory guard. The cap-check + putIfAbsent
        // pair must be atomic so a stampede of concurrent installs at sessions.size()
        // == MAX-1 cannot all pass the check and overshoot the ceiling.
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder holder = new GateHolder("hash-1");
        final DebugSessionRegistry registry = registryWith(holder, key);

        final int contestants = DebugSessionRegistry.MAX_ACTIVE_SESSIONS + 50;
        final ExecutorService pool = Executors.newFixedThreadPool(32);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(contestants);
        final AtomicInteger installed = new AtomicInteger();
        final AtomicInteger tooMany = new AtomicInteger();
        try {
            for (int i = 0; i < contestants; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        final InstallOutcome outcome = registry.installWithId(
                            UUID.randomUUID().toString(), key, "client-a",
                            SessionLimits.DEFAULT);
                        switch (outcome.getStatus()) {
                            case INSTALLED:
                                installed.incrementAndGet();
                                break;
                            case TOO_MANY_SESSIONS:
                                tooMany.incrementAndGet();
                                break;
                            default:
                                break;
                        }
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent installs hung");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(DebugSessionRegistry.MAX_ACTIVE_SESSIONS, installed.get(),
                     "INSTALLED count must equal the ceiling exactly — no slot was overshot");
        assertEquals(contestants - DebugSessionRegistry.MAX_ACTIVE_SESSIONS, tooMany.get(),
                     "every install above the ceiling must return TOO_MANY_SESSIONS");
        assertEquals(DebugSessionRegistry.MAX_ACTIVE_SESSIONS, registry.snapshotActive().size(),
                     "registry size must match the ceiling");
        assertEquals(DebugSessionRegistry.MAX_ACTIVE_SESSIONS, holder.getRecorders().length,
                     "every INSTALLED outcome must have bound a recorder on the holder");
    }

    @Test
    public void reapExpired_removesPastDeadlineSessions() throws InterruptedException {
        final RuleKey key = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final GateHolder holder = new GateHolder("hash-1");
        final DebugSessionRegistry registry = registryWith(holder, key);
        final SessionLimits shortRetention = new SessionLimits(SessionLimits.MAX_RECORD_CAP, 1L);
        registry.install(key, "client-a", shortRetention);
        Thread.sleep(5);

        final int reaped = registry.reapExpired(System.currentTimeMillis());

        assertEquals(1, reaped);
        assertFalse(holder.isGateOn());
        assertEquals(0, registry.snapshotActive().size());
    }

    private static DebugSessionRegistry registryWith(final GateHolder holder, final RuleKey key) {
        final DebugSessionRegistry registry = new DebugSessionRegistry();
        registry.registerLookup(staticLookup(key, holder));
        registry.registerRecorderFactory(new TrivialRecorderFactory());
        return registry;
    }

    private static DebugHolderLookup staticLookup(final RuleKey key, final GateHolder holder) {
        return new DebugHolderLookup() {
            @Override
            public boolean serves(final RuleKey candidate) {
                return candidate.getCatalog() == key.getCatalog();
            }

            @Override
            public GateHolder lookup(final RuleKey candidate) {
                return key.equals(candidate) ? holder : null;
            }
        };
    }

    private static final class MutableLookup implements DebugHolderLookup {
        private RuleKey key;
        private GateHolder holder;

        void bind(final RuleKey key, final GateHolder holder) {
            this.key = key;
            this.holder = holder;
        }

        @Override
        public boolean serves(final RuleKey candidate) {
            return key != null && candidate.getCatalog() == key.getCatalog();
        }

        @Override
        public GateHolder lookup(final RuleKey candidate) {
            return key != null && key.equals(candidate) ? holder : null;
        }
    }

    private static final class TrivialRecorderFactory implements DebugRecorderFactory {
        @Override
        public boolean serves(final RuleKey key) {
            return true;
        }

        @Override
        public AbstractDebugRecorder create(final String sessionId, final RuleKey key,
                                            final GateHolder boundHolder, final SessionLimits limits) {
            return new AbstractDebugRecorder(sessionId, key, boundHolder, limits) {
            };
        }
    }
}
