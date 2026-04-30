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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks every retired {@link RuleClassLoader} through a {@link ReferenceQueue} of
 * {@link PhantomReference}s so GC of the loader is observable rather than silent.
 *
 * <p>Dropping a bundle's strong reference to its loader is necessary but not sufficient for
 * class unloading: any lingering reference (a handler left registered, a sample still sitting
 * in a DataCarrier slot, a Javassist CtClass left attached in the default pool, an async task
 * holding the class) will pin the loader and its classes indefinitely. Without observability,
 * that kind of leak is invisible until heap pressure surfaces hours later.
 *
 * <p>This graveyard is internal to {@link DSLClassLoaderManager}. The manager retires loaders
 * here via {@code dropRuntime} (full teardown) and {@code retire} (engine-decided "displaced
 * prior is dead"); a daemon sweeper thread the manager owns drains collected phantoms +
 * WARNs on stale entries. No external caller touches this class — every consumer goes
 * through the manager.
 */
@Slf4j
final class ClassLoaderGc {

    private final ReferenceQueue<RuleClassLoader> queue = new ReferenceQueue<>();
    private final Map<PhantomReference<RuleClassLoader>, Retired> pending = new ConcurrentHashMap<>();

    @Getter
    private final AtomicLong collectedTotal = new AtomicLong();

    /**
     * Register a loader as retired. The caller must drop the last strong reference it holds
     * to {@code loader} immediately after this call — otherwise the phantom reference will
     * never be enqueued and the pending entry will stay forever.
     */
    void retire(final RuleClassLoader loader) {
        if (loader == null) {
            return;
        }
        final PhantomReference<RuleClassLoader> ref = new PhantomReference<>(loader, queue);
        final Retired retired = new Retired(
            loader.getKind(), loader.getCatalog(), loader.getRule(), loader.getContentHash(),
            System.currentTimeMillis(), ref);
        pending.put(ref, retired);
    }

    /**
     * Drain collected phantoms from the queue. Returns the entries the JVM confirmed as
     * unreachable since the last sweep. Entries that remain in {@link #pending()} after this
     * call are still suspected leaks. Called by the manager's internal sweeper thread.
     */
    Collection<Retired> sweep() {
        final java.util.ArrayList<Retired> drained = new java.util.ArrayList<>();
        Reference<?> r;
        while ((r = queue.poll()) != null) {
            final Retired done = pending.remove(r);
            if (done != null) {
                collectedTotal.incrementAndGet();
                log.info("rule loader collected: {}:{}/{} hash={} ttg={}ms",
                    done.kind() == DSLClassLoaderManager.Kind.BUNDLED ? "bundled" : "runtime-rule",
                    done.catalog().getWireName(), done.rule(), done.contentHashShort(),
                    System.currentTimeMillis() - done.retiredAtMs());
                drained.add(done);
            }
        }
        return drained;
    }

    /**
     * @return snapshot of retired-but-not-yet-GC'd entries. Elevated steadily across many
     *         sweeps == leak; the manager's sweeper logs WARN per entry older than the
     *         configured threshold.
     */
    Collection<Retired> pending() {
        return Collections.unmodifiableCollection(pending.values());
    }

    /** Informational record surfaced to the sweeper. Identity is immutable; only the
     *  {@code warned} latch can flip, and only once per lifetime of this {@code Retired}. */
    static final class Retired {
        private final DSLClassLoaderManager.Kind kind;
        private final Catalog catalog;
        private final String rule;
        private final String contentHash;
        private final long retiredAtMs;
        @SuppressWarnings("unused") // strong reference retained so the phantom isn't itself GC'd
        private final PhantomReference<RuleClassLoader> ref;
        /** Single-shot latch flipped by {@link #markWarnedIfNotAlready()} so the stale-loader
         *  WARN fires once per retired loader rather than once per sweep per entry. */
        private final AtomicBoolean warned = new AtomicBoolean(false);

        Retired(final DSLClassLoaderManager.Kind kind, final Catalog catalog, final String rule,
                final String contentHash, final long retiredAtMs,
                final PhantomReference<RuleClassLoader> ref) {
            this.kind = kind;
            this.catalog = catalog;
            this.rule = rule;
            this.contentHash = contentHash;
            this.retiredAtMs = retiredAtMs;
            this.ref = ref;
        }

        DSLClassLoaderManager.Kind kind() {
            return kind;
        }

        Catalog catalog() {
            return catalog;
        }

        String rule() {
            return rule;
        }

        String contentHash() {
            return contentHash;
        }

        long retiredAtMs() {
            return retiredAtMs;
        }

        String contentHashShort() {
            if (contentHash == null || contentHash.length() <= 8) {
                return contentHash == null ? "none" : contentHash;
            }
            return contentHash.substring(0, 8);
        }

        boolean markWarnedIfNotAlready() {
            return warned.compareAndSet(false, true);
        }
    }
}
