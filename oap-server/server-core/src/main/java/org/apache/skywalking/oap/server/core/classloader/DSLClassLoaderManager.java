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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Process-wide owner of every per-file {@link RuleClassLoader} the OAP creates for a DSL rule
 * (MAL, LAL, future OAL). Both static-bundled rules (after a runtime override is removed and
 * the bundled rule must serve again) and runtime-pushed overrides go through this singleton,
 * so there is one place to: mint loaders with a uniform name format, retire them when a newer
 * version replaces them, and observe the JVM's collection of retired loaders so leaks surface
 * as a WARN instead of silent heap growth.
 *
 * <p><b>Why a singleton, not a {@code Service}.</b> Loaders are foundational JVM state — the
 * meter / log compile paths reach for {@code DSLClassLoaderManager.INSTANCE} from places that
 * have no {@code ModuleManager} in scope (the LAL / MAL applier static methods, test
 * fixtures). Threading the manager through every constructor would be churn for zero benefit;
 * lifetime is the JVM's, not a module's.
 *
 * <p><b>Static fall-over contract.</b> Bundled rules at boot are loaded the same way as
 * before — into the OAP main classloader, no per-file static loader, no entry in this map.
 * Per-file static loaders only appear after a runtime override on a bundled rule is removed
 * (via {@code /inactivate} or {@code /delete}): the runtime loader retires here, then the
 * engine reloads the bundled YAML from {@code StaticRuleRegistry} and calls
 * {@link #newBuilder} with {@link Kind#BUNDLED} to mint a fresh loader hosting the bundled
 * compile output. So at any moment there is at most one per-file loader for a given key, and
 * only when the key has actually fallen over.
 *
 * <p><b>GC sweep is internal.</b> A daemon-thread scheduled executor inside this manager
 * polls the phantom queue + WARNs on stale entries; no external code calls a sweep API. The
 * sweeper starts lazily on the first {@link #newBuilder} call so tests that touch
 * {@link RuleClassLoader} directly never spawn the thread.
 */
@Slf4j
public final class DSLClassLoaderManager {

    /** Origin of a loader. {@code RUNTIME} loaders host operator-pushed runtime-rule overrides;
     *  {@code BUNDLED} loaders host bundled rules brought back into service after a runtime
     *  override on the same key was removed. The active loader for a given key is always at
     *  most one; manager keys are {@code (catalog, rule)}, not {@code (catalog, rule, kind)}. */
    public enum Kind {
        BUNDLED, RUNTIME
    }

    /** Process-wide singleton. */
    public static final DSLClassLoaderManager INSTANCE = new DSLClassLoaderManager();

    /** Sweep cadence for the internal phantom-queue drainer. 30 s — comfortably past a typical
     *  young-gen pause cycle so most retired loaders surface as collected within a couple of
     *  ticks; short enough that a leaked loader's WARN doesn't wait minutes. */
    private static final long SWEEP_INTERVAL_SECONDS = 30L;
    /** A retired loader still alive past this threshold is WARN'd as a suspected leak. 5 min —
     *  long enough that a slow GC cycle doesn't cry wolf, short enough that an actual leak is
     *  surfaced before heap pressure triggers a full GC pause. */
    private static final long STALE_LOADER_WARN_THRESHOLD_MS = 5L * 60L * 1000L;

    private final Map<Key, RuleClassLoader> active = new ConcurrentHashMap<>();
    private final ClassLoaderGc graveyard = new ClassLoaderGc();
    private final AtomicBoolean sweeperStarted = new AtomicBoolean(false);
    /** Captured on first {@link #newBuilder} call so DSL compile paths can mint loaders before
     *  the OAP main classloader is fully initialised in surprising boot orders. Volatile read
     *  so the lazy init publishes safely to other threads. */
    private volatile ClassLoader capturedParent;

    private DSLClassLoaderManager() {
    }

    /**
     * Mint a fresh loader for {@code (catalog, rule)}. The loader is returned for the caller
     * to compile classes into; it is <b>not yet</b> registered as the active loader for this
     * key. The caller promotes it via {@link #commit(RuleClassLoader)} after a successful
     * compile / register. A failed compile simply discards the returned loader (no
     * displacement, no false leak signal in {@link #pendingCount()}).
     *
     * <p>This split exists because the loader has to exist <em>during</em> compile (Javassist
     * defines classes into it), but the manager's "active" view should reflect only loaders
     * whose rule successfully reached the dispatcher. Otherwise a transient compile failure
     * would replace the diagnostic record while the prior bundle is still actually serving.
     */
    public RuleClassLoader newBuilder(final Catalog catalog, final String rule, final Kind kind,
                                       final String contentHash) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(kind, "kind");
        ensureSweeperStarted();
        final ClassLoader parent = resolveParent();
        return new RuleClassLoader(kind, catalog, rule, contentHash, parent);
    }

    /**
     * Promote a freshly-compiled loader to the active slot for its {@code (catalog, rule)}.
     * Returns the loader that was active before the swap (if any) so the caller can decide
     * whether to retire it: MAL STRUCTURAL / NEW commit and LAL commit retire the prior;
     * MAL FILTER_ONLY commit does not (the prior loader's {@code Metrics} subclasses are
     * still the storage target via {@code MeterSystem.meterPrototypes}).
     *
     * <p>{@link #newBuilder} on its own does not register the loader in {@code active};
     * {@code commit} is the only path that does, so a failed apply leaves the active map
     * pointing at whatever was there before.
     */
    public Optional<RuleClassLoader> commit(final RuleClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        final Key key = new Key(loader.getCatalog(), loader.getRule());
        final RuleClassLoader prior = active.put(key, loader);
        return Optional.ofNullable(prior);
    }

    /**
     * Send a loader to the internal graveyard for collection observability. Used by engine
     * commit paths that displace a prior loader and know it should be GC'd (the {@link
     * #commit} return value carries the prior loader for exactly this purpose). The
     * graveyard's daemon sweeper logs INFO when the JVM confirms collection and WARN when a
     * retired loader stays alive past the threshold.
     */
    public void retire(final RuleClassLoader loader) {
        if (loader == null) {
            return;
        }
        ensureSweeperStarted();
        graveyard.retire(loader);
    }

    /**
     * Drop the active loader (regardless of {@link Kind}) for {@code (catalog, rule)} and
     * retire it via the graveyard. Returns the loader that was active before the drop, or
     * {@link Optional#empty()} when no loader was registered (already dropped, or never
     * installed). Used by the engine's full-teardown path (unregister) to remove the active
     * loader and observe its eventual GC.
     */
    public Optional<RuleClassLoader> dropRuntime(final Catalog catalog, final String rule) {
        final Key key = new Key(catalog, rule);
        final RuleClassLoader current = active.remove(key);
        if (current == null) {
            return Optional.empty();
        }
        graveyard.retire(current);
        return Optional.of(current);
    }

    /**
     * Diagnostic — current loader for {@code (catalog, rule)} if any. Used by tests and by
     * the runtime-rule {@code /list} surface to show whether a key is currently served by a
     * runtime override or a static fall-over.
     */
    public Optional<RuleClassLoader> active(final Catalog catalog, final String rule) {
        return Optional.ofNullable(active.get(new Key(catalog, rule)));
    }

    /**
     * Diagnostic — number of currently-active loaders the manager owns (one per
     * {@code (catalog, rule)} key with at least one successful {@link #newBuilder} that
     * hasn't been {@link #dropRuntime}d). Surfaced for operator visibility through whichever
     * receiver exposes loader stats; the runtime-rule REST handler can join this with its
     * own {@code /list} per-rule view if the operator wants both numbers in one place.
     */
    public int activeCount() {
        return active.size();
    }

    /**
     * Diagnostic — number of retired loaders the JVM has not yet collected. Steady-state
     * elevated reading is the leak signal the sweeper WARNs on, surfaced here so operators
     * can graph it independently.
     */
    public int pendingCount() {
        return graveyard.pending().size();
    }

    /** Lazy parent-classloader capture. First call wins; subsequent calls return the cached
     *  reference. {@code Thread.currentThread().getContextClassLoader()} is the OAP app
     *  loader at every realistic call site (DSL compile is driven from receiver / analyzer
     *  threads after module boot). */
    private ClassLoader resolveParent() {
        ClassLoader local = capturedParent;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = capturedParent;
            if (local == null) {
                local = Thread.currentThread().getContextClassLoader();
                if (local == null) {
                    local = ClassLoader.getSystemClassLoader();
                }
                capturedParent = local;
            }
            return local;
        }
    }

    private void ensureSweeperStarted() {
        if (sweeperStarted.compareAndSet(false, true)) {
            final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "dsl-classloader-gc");
                t.setDaemon(true);
                return t;
            });
            exec.scheduleWithFixedDelay(this::sweepInternal,
                SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void sweepInternal() {
        try {
            final Collection<ClassLoaderGc.Retired> collected = graveyard.sweep();
            if (!collected.isEmpty() && log.isDebugEnabled()) {
                log.debug("dsl-classloader-gc: {} loader(s) confirmed collected", collected.size());
            }
            final long nowMs = System.currentTimeMillis();
            for (final ClassLoaderGc.Retired r : graveyard.pending()) {
                final long ageMs = nowMs - r.retiredAtMs();
                if (ageMs > STALE_LOADER_WARN_THRESHOLD_MS && r.markWarnedIfNotAlready()) {
                    log.warn("rule loader leak suspected: {}:{}/{} hash={} pending {} ms "
                            + "(threshold {}). Check for lingering handler registrations or "
                            + "samples buffered in DataCarrier partitions.",
                        r.kind() == Kind.BUNDLED ? "bundled" : "runtime-rule",
                        r.catalog().getWireName(), r.rule(), r.contentHashShort(), ageMs,
                        STALE_LOADER_WARN_THRESHOLD_MS);
                }
            }
        } catch (final Throwable t) {
            log.warn("dsl-classloader-gc sweep failed; will retry next interval", t);
        }
    }

    /** Composite key — equality + hashCode are catalog-and-rule, never include the loader
     *  identity. ConcurrentHashMap keys must be hash-stable. */
    private static final class Key {
        private final Catalog catalog;
        private final String rule;

        Key(final Catalog catalog, final String rule) {
            this.catalog = catalog;
            this.rule = rule;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            final Key k = (Key) o;
            return catalog == k.catalog && rule.equals(k.rule);
        }

        @Override
        public int hashCode() {
            return 31 * catalog.hashCode() + rule.hashCode();
        }
    }
}
