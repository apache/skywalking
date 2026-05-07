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

package org.apache.skywalking.oap.server.receiver.runtimerule.reconcile;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.metrics.LockMetrics;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

/**
 * Loads static rule files (the on-disk rules the catalog loaders compiled at module start)
 * into the runtime-rule dslManager's view of the world. Two entry points:
 *
 * <ul>
 *   <li>{@link #loadAll} — boot-time load. Asks every engine to load its catalog's static
 *       rules into the engine's internal applied state (via
 *       {@link RuleEngine#recordBundledClaims}), then seeds the shared
 *       {@code appliedContent} + {@code snapshot} maps so the first {@code /addOrUpdate}
 *       classifier and the first Suspend lookup see the bundle.</li>
 *   <li>{@link #loadIfMissing} — tick-time load. Re-loads any static rule whose DB row got
 *       {@code /delete}d while leaving the disk content intact; without this the rule would
 *       stay dormant until the next OAP restart, contradicting {@code /delete}'s
 *       "rule reverts to disk version" promise.</li>
 * </ul>
 *
 * <p><b>Why this class exists.</b> {@code MeterProcessService},
 * {@code OpenTelemetryMetricRequestProcessor}, and the LAL {@code Factory} compile and
 * register static rules during their own module {@code start()} — by the time the runtime-
 * rule receiver boots, MeterSystem has prototypes, MetricsStreamProcessor has workers, and
 * the LAL factory has handlers, all live. But the runtime-rule dslManager doesn't see any
 * of it because those registrations happened outside its pipeline. Without loading:
 * <ul>
 *   <li>The first {@code /inactivate} against a shipped static rule no-ops (engine sees no
 *       prior applied entry); handlers keep serving the rule the operator just paused.</li>
 *   <li>The first {@code /addOrUpdate} classifies against {@code priorContent == null} and
 *       returns {@code NEW} even on a filter-only edit, mis-computing the shape-break /
 *       removed-metrics sets.</li>
 *   <li>Cluster Suspend RPCs return {@code NOT_PRESENT} (snapshot misses), bypassing the
 *       suspend window for that bundle.</li>
 * </ul>
 *
 * <p><b>DSL-agnostic.</b> The actual per-DSL load — building a synthetic Applied artifact
 * with metric names (MAL) or registered-rule list (LAL) — happens behind
 * {@link RuleEngine#recordBundledClaims}. This class only iterates {@code StaticRuleRegistry},
 * routes each entry to the matching engine, and updates the shared scheduler-side state on
 * success.
 */
@Slf4j
public final class StaticRuleLoader {

    private final RuleEngineRegistry engineRegistry;
    private final Map<String, AppliedRuleScript> rules;
    private final LockMetrics lockMetrics;
    /** Tick-time per-file apply driver for {@link #loadIfMissing}. Bound at construction so
     *  this class doesn't depend on DSLManager directly. */
    private final ApplyOne applyOne;

    public StaticRuleLoader(final RuleEngineRegistry engineRegistry,
                            final Map<String, AppliedRuleScript> rules,
                            final LockMetrics lockMetrics,
                            final ApplyOne applyOne) {
        this.engineRegistry = engineRegistry;
        this.rules = rules;
        this.lockMetrics = lockMetrics;
        this.applyOne = applyOne;
    }

    /**
     * Boot-time load: for every {@code (catalog, name)} in {@link StaticRuleRegistry}, ask
     * the matching engine to load it. On success, also load the shared {@code appliedContent}
     * + {@code snapshot} maps so the first {@code /addOrUpdate} classifier and the first
     * Suspend lookup see the bundle.
     */
    public void loadAll() {
        final Map<String, String> entries = StaticRuleRegistry.active().entries();
        if (entries.isEmpty()) {
            return;
        }
        int loaded = 0;
        final long nowMs = System.currentTimeMillis();
        for (final Map.Entry<String, String> e : entries.entrySet()) {
            final String[] parts = StaticRuleRegistry.splitKey(e.getKey());
            if (parts == null) {
                continue;
            }
            final String catalog = parts[0];
            final String name = parts[1];
            final RuleEngine<?> engine = engineRegistry.forCatalog(catalog);
            if (engine == null) {
                continue;
            }
            final String content = e.getValue();
            if (!engine.recordBundledClaims(catalog, name, content)) {
                continue;
            }
            final String key = DSLScriptKey.key(catalog, name);
            final String contentHash = ContentHash.sha256Hex(content);
            // recordBundledClaims has already stamped the synthetic Applied into the
            // rules map (with content=null and state=null). Overlay the bundled content
            // and a RUNNING state on that entry — without these, the first REST
            // /addOrUpdate would classify against null prior content and return NEW even
            // on a filter-only edit, and the first Suspend RPC would lookup-miss.
            // putIfAbsent would no-op here because the engine already created the entry.
            rules.compute(key, (k, prev) -> {
                final DSLRuntimeState state =
                    DSLRuntimeState.running(catalog, name, contentHash, nowMs);
                return prev == null
                    ? new AppliedRuleScript(catalog, name, content, state)
                    : prev.withContentAndState(content, state);
            });
            loaded++;
        }
        if (loaded > 0) {
            log.info("runtime-rule dslManager: loaded {} static rule file(s) from "
                + "StaticRuleRegistry — /inactivate, /addOrUpdate classify, and Suspend "
                + "broadcast now cover shipped static rules.", loaded);
        }
    }

    /**
     * Tick-time load: re-applies static rules whose DB row got {@code /delete}d while leaving
     * disk content intact. Skips rules with a DB row this tick (operator state wins) and
     * rules already tracked in {@code snapshot} (boot load or prior tick covered them).
     * Uses tryLock so a racing REST workflow defers to the next tick.
     */
    public void loadIfMissing(final Set<String> seenKeys, final long nowMs,
                              final StorageManipulationOpt tickOpt) {
        final Map<String, String> entries = StaticRuleRegistry.active().entries();
        if (entries.isEmpty()) {
            return;
        }
        for (final Map.Entry<String, String> e : entries.entrySet()) {
            final String[] parts = StaticRuleRegistry.splitKey(e.getKey());
            if (parts == null) {
                continue;
            }
            final String catalog = parts[0];
            final String name = parts[1];
            final String key = DSLScriptKey.key(catalog, name);
            if (seenKeys.contains(key)) {
                continue;
            }
            // Snapshot presence is the scheduler's "is this bundle tracked?" signal — engine
            // ownership lives behind recordBundledClaims. If snapshot has the key, either the
            // engine has it loaded or a runtime apply did it; either way nothing to redo.
            if (rules.containsKey(key)) {
                continue;
            }
            final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
            if (!lockMetrics.tryAcquireForSyncTimer(perFile, catalog, name)) {
                continue;
            }
            try (HistogramMetrics.Timer ignored = lockMetrics.startSyncTimerHoldTimer()) {
                if (rules.containsKey(key)) {
                    continue;
                }
                final String content = e.getValue();
                final String hash = ContentHash.sha256Hex(content);
                final RuntimeRuleManagementDAO.RuntimeRuleFile synthetic =
                    new RuntimeRuleManagementDAO.RuntimeRuleFile(
                        catalog, name, content, "ACTIVE", nowMs);
                log.info("runtime-rule dslManager: re-loading static rule {}/{} from "
                    + "StaticRuleRegistry (no DB row, no applied state)", catalog, name);
                applyOne.applyOneRuleFile(synthetic, hash, null, nowMs, key, false, tickOpt);
            } finally {
                perFile.unlock();
            }
        }
    }

    /** Per-file apply handle, supplied by DSLManager. */
    @FunctionalInterface
    public interface ApplyOne {
        void applyOneRuleFile(RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                              String newHash, DSLRuntimeState prev, long nowMs, String key,
                              boolean deferCommit, StorageManipulationOpt storageOpt);
    }
}
