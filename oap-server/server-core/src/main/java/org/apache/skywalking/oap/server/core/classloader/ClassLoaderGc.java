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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
 * <p><b>Leak evidence, not wall-clock guessing.</b> A classloader that has defined classes is
 * only reclaimed by a class-unloading-capable GC cycle (G1 concurrent mark / full GC — young
 * collections never unload classes), and an idle heap may not run one for hours. So "retired
 * N minutes ago and still uncollected" is NOT a leak signal by itself. To tell a pinned
 * loader apart from plain GC inactivity, the graveyard arms an <em>unload probe</em> whenever
 * entries are pending: a parent-less throwaway classloader that defines one empty class
 * ({@link UnloadProbePayload}) and is immediately dereferenced. The probe has the exact same
 * collection requirement as a retired rule loader, so its collection is proof that a
 * class-unloading cycle completed after the probe was minted. {@link #leakSuspects} flags an
 * entry only when such a cycle ran comfortably after the entry's retirement and the entry
 * still survived it.
 *
 * <p>This graveyard is internal to {@link DSLClassLoaderManager}. The manager retires loaders
 * here via {@code dropRuntime} (full teardown) and {@code retire} (engine-decided "displaced
 * prior is dead"); a daemon sweeper thread the manager owns drains collected phantoms +
 * WARNs on evidence-backed suspects. No external caller touches this class — every consumer
 * goes through the manager.
 */
@Slf4j
final class ClassLoaderGc {

    private static final String PROBE_PAYLOAD_CLASS = UnloadProbePayload.class.getName();
    private static final byte[] PROBE_PAYLOAD_BYTECODE = readProbePayloadBytecode();

    private final ReferenceQueue<RuleClassLoader> queue = new ReferenceQueue<>();
    private final Map<PhantomReference<RuleClassLoader>, Retired> pending = new ConcurrentHashMap<>();

    private final ReferenceQueue<ClassLoader> probeQueue = new ReferenceQueue<>();
    private final Object probeLock = new Object();
    /** Live probe generation; at most one at a time. Guarded by {@link #probeLock}. The
     *  phantom ref must stay strongly held here or it would be GC'd before enqueuing. */
    private PhantomReference<ClassLoader> liveProbe;
    private long liveProbeMintedAtMs;
    /** Latest probe mint-time for which a class-unloading GC cycle is confirmed complete.
     *  {@code 0} until the first probe collection is observed. */
    private volatile long unloadEvidenceUpToMs;

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
        armUnloadProbe();
    }

    /**
     * Drain collected phantoms from the queue. Returns the entries the JVM confirmed as
     * unreachable since the last sweep. Entries that remain in {@link #pending()} after this
     * call have not been collected yet — {@link #leakSuspects} decides which of them are
     * evidence-backed leaks. Called by the manager's internal sweeper thread.
     */
    Collection<Retired> sweep() {
        drainUnloadProbe();
        final List<Retired> drained = new ArrayList<>();
        Reference<?> r;
        while ((r = queue.poll()) != null) {
            final Retired done = pending.remove(r);
            if (done != null) {
                collectedTotal.incrementAndGet();
                log.info("rule loader collected: {}:{}/{} hash={} ttg={}ms{}",
                    done.kind() == DSLClassLoaderManager.Kind.BUNDLED ? "bundled" : "runtime-rule",
                    done.catalog().getWireName(), done.rule(), done.contentHashShort(),
                    System.currentTimeMillis() - done.retiredAtMs(),
                    done.warnedAlready()
                        ? " — the earlier leak warning is cleared, the lingering reference has been released"
                        : "");
                drained.add(done);
            }
        }
        // Re-arm so entries retired after the previous probe was minted get their own
        // evidence generation on a later class-unloading cycle.
        if (!pending.isEmpty()) {
            armUnloadProbe();
        }
        return drained;
    }

    /**
     * @return snapshot of retired-but-not-yet-GC'd entries. Elevated steadily across many
     *         sweeps == leak; the manager's sweeper logs WARN per entry that
     *         {@link #leakSuspects} confirms against unload evidence.
     */
    Collection<Retired> pending() {
        return Collections.unmodifiableCollection(pending.values());
    }

    /**
     * Entries whose leak WARN should fire now: a class-unloading GC cycle is confirmed to
     * have completed at least {@code settleMs} after the entry's retirement, and the entry
     * survived it — so something still strongly references the loader; GC inactivity is
     * ruled out. The settle window keeps legitimately transient holders (an apply call
     * chain still carrying the displaced bundle on a request thread while the cycle ran)
     * from tripping the detector. Each entry is returned at most once across the graveyard's
     * lifetime (single-shot {@code warned} latch).
     *
     * <p>If the probe payload bytecode is unavailable (unreadable resource — effectively
     * never), evidence can't be produced; the check degrades to the wall-clock heuristic
     * rather than never warning at all.
     */
    Collection<Retired> leakSuspects(final long settleMs) {
        final List<Retired> out = new ArrayList<>();
        final long evidenceUpToMs = unloadEvidenceUpToMs;
        final long nowMs = System.currentTimeMillis();
        for (final Retired r : pending.values()) {
            final boolean suspect = PROBE_PAYLOAD_BYTECODE == null
                ? nowMs - r.retiredAtMs() > settleMs
                : evidenceUpToMs >= r.retiredAtMs() + settleMs;
            if (suspect && r.markWarnedIfNotAlready()) {
                out.add(r);
            }
        }
        return out;
    }

    /** Latest probe mint-time confirmed survived-past by a class-unloading GC cycle;
     *  {@code 0} while no cycle has been observed. Exposed for the manager's diagnostics
     *  and for deterministic tests. */
    long unloadEvidenceUpToMs() {
        return unloadEvidenceUpToMs;
    }

    /** Whether the unload probe can produce evidence. {@code false} only when the payload
     *  bytecode resource is unreadable — {@link #leakSuspects} then degrades to the
     *  wall-clock heuristic, and the manager's WARN wording must not claim GC evidence. */
    boolean unloadProbeUsable() {
        return PROBE_PAYLOAD_BYTECODE != null;
    }

    /**
     * Record that a class-unloading GC cycle completed after {@code probeMintedAtMs}.
     * Normally driven by {@link #drainUnloadProbe()}; package-private so tests can exercise
     * {@link #leakSuspects} without depending on real GC timing.
     */
    void recordUnloadEvidence(final long probeMintedAtMs) {
        synchronized (probeLock) {
            if (probeMintedAtMs > unloadEvidenceUpToMs) {
                unloadEvidenceUpToMs = probeMintedAtMs;
            }
        }
    }

    /** Mint a fresh probe if none is live. The probe loader leaves this method with the
     *  phantom reference as its only remaining reference, so the next class-unloading GC
     *  cycle is guaranteed to collect it. */
    private void armUnloadProbe() {
        if (PROBE_PAYLOAD_BYTECODE == null) {
            return;
        }
        synchronized (probeLock) {
            if (liveProbe != null) {
                return;
            }
            final ProbeClassLoader probe = new ProbeClassLoader();
            probe.definePayload();
            liveProbe = new PhantomReference<>(probe, probeQueue);
            liveProbeMintedAtMs = System.currentTimeMillis();
        }
    }

    private void drainUnloadProbe() {
        boolean collected = false;
        while (probeQueue.poll() != null) {
            collected = true;
        }
        if (collected) {
            synchronized (probeLock) {
                recordUnloadEvidence(liveProbeMintedAtMs);
                liveProbe = null;
            }
        }
    }

    private static byte[] readProbePayloadBytecode() {
        final String resource = "/" + PROBE_PAYLOAD_CLASS.replace('.', '/') + ".class";
        try (InputStream in = ClassLoaderGc.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("unload-probe payload bytecode not found at {}; "
                    + "loader leak detection degrades to the wall-clock heuristic", resource);
                return null;
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            final byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (final IOException e) {
            log.warn("failed to read unload-probe payload bytecode; "
                + "loader leak detection degrades to the wall-clock heuristic", e);
            return null;
        }
    }

    /** Parent-less loader whose single defined class subjects it to class-unloading
     *  collection semantics. Never asked to load anything. */
    private static final class ProbeClassLoader extends ClassLoader {
        ProbeClassLoader() {
            super(null);
        }

        void definePayload() {
            defineClass(PROBE_PAYLOAD_CLASS, PROBE_PAYLOAD_BYTECODE, 0, PROBE_PAYLOAD_BYTECODE.length);
        }
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

        boolean warnedAlready() {
            return warned.get();
        }
    }
}
