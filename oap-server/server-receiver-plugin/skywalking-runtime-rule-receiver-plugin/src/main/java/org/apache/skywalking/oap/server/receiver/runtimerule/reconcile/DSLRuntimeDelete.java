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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyContext;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * Destructive {@code /delete} pipeline. Third orchestrator alongside {@link DSLRuntimeApply}
 * (NEW / FILTER_ONLY / STRUCTURAL apply) and {@link DSLRuntimeUnregister} (INACTIVE / gone-keys
 * tear-down). {@code /delete} is the one endpoint that physically drops backend schema —
 * {@code /inactivate} preserves it for cheap re-activation.
 *
 * <p>This orchestrator is a thin dispatcher: it acquires the per-file lock, runs the cross-
 * file ownership guard (defence-in-depth — {@code /addOrUpdate} should have caught it
 * already), and routes to {@link RuleEngine#dropBackend}. Engines that own backend
 * schema (MAL) execute the re-register-then-drop dance there; engines without backend (LAL)
 * implement the SPI method as a no-op.
 *
 * <p>The caller (REST {@code /delete}) holds the per-file lock; this orchestrator re-acquires
 * it (lock is reentrant) so the implementation is correct whether called inline or from a
 * background path.
 */
@Slf4j
public class DSLRuntimeDelete {

    private final RuleEngineRegistry engineRegistry;
    private final ModuleManager moduleManager;
    private final Map<String, AppliedRuleScript> rules;
    private final Consumer<Set<String>> alarmResetter;

    public DSLRuntimeDelete(final RuleEngineRegistry engineRegistry,
                            final ModuleManager moduleManager,
                            final Map<String, AppliedRuleScript> rules,
                            final Consumer<Set<String>> alarmResetter) {
        this.engineRegistry = engineRegistry;
        this.moduleManager = moduleManager;
        this.rules = rules;
        this.alarmResetter = alarmResetter;
    }

    /**
     * Discharge backend debt for the {@code (catalog, name)} bundle the REST handler is about
     * to {@code /delete}. Routes to {@link RuleEngine#dropBackend} — engines that own
     * backend schema do the re-register-then-drop dance; engines without backend no-op.
     *
     * @throws IllegalStateException if a cross-file ownership conflict is detected, or the
     *     engine cannot discharge its backend debt (MeterSystem unavailable, parse error in
     *     the inactive content). The caller (REST handler) aborts {@code dao.delete} on this
     *     throw — refusing to delete the row is the correct failure mode.
     */
    public void dropBackendForDelete(final String catalog, final String name, final String content) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(catalog);
        if (engine == null) {
            log.warn("runtime-rule dslManager: no engine registered for catalog '{}' on "
                + "/delete of {}/{}; skipping", catalog, catalog, name);
            return;
        }
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
        perFile.lock();
        try {
            // Defence-in-depth ownership guard. /addOrUpdate's check should have prevented
            // this — if a race or DAO blip slipped one through, dropping the backend resource
            // here would tear down a metric another active file is still using.
            final List<String> activeConflicts = checkOwnershipConflicts(engine, catalog, name, content);
            if (!activeConflicts.isEmpty()) {
                throw new IllegalStateException(
                    "/delete refused for " + catalog + "/" + name + ": claim(s) "
                        + activeConflicts + " are now owned by another active bundle. "
                        + "The /addOrUpdate cross-file ownership check should have caught "
                        + "this; this is a safety net. Update or /inactivate the conflicting "
                        + "bundle(s) first.");
            }
            // The engine's dropBackend handles both modes via bundledContent:
            //   * null     → destructive cascade (drop everything runtime claimed)
            //   * non-null → delta drop (only runtime-only + shape-break metrics; bundled-
            //                 shared at matching shape is preserved for bundled to reuse on
            //                 its synchronous reload below).
            final String bundledContent =
                StaticRuleRegistry.active().find(catalog, name).orElse(null);
            if (bundledContent != null) {
                log.info("runtime-rule /delete: bundled twin exists for {}/{} — running "
                    + "delta-aware cleanup (drop runtime-only / shape-break, keep bundled-shared)",
                    catalog, name);
            }
            dropBackend(engine, catalog, name, content, bundledContent);
        } finally {
            perFile.unlock();
        }
    }

    private List<String> checkOwnershipConflicts(final RuleEngine<?> engine, final String catalog,
                                                 final String name, final String content) {
        final String selfKey = DSLScriptKey.key(catalog, name);
        final Set<String> planned = engine.claimedKeys(content, catalog + "/" + name);
        final List<String> conflicts = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> other : engine.activeClaimsExcluding(selfKey).entrySet()) {
            for (final String pk : planned) {
                if (other.getValue().contains(pk)) {
                    conflicts.add(pk + " owned by " + other.getKey());
                }
            }
        }
        return conflicts;
    }

    /**
     * Synchronously reload the bundled rule into a fresh {@code static:} loader after a
     * {@code /delete} of a row whose {@code (catalog, name)} has a bundled YAML on disk.
     * The REST handler calls this so the operator's response reflects the post-delete
     * reality (bundled is already serving) rather than waiting for the next tick.
     *
     * @return {@code true} when a bundled rule was reloaded; {@code false} when no bundled
     *         twin exists or the engine doesn't participate in static fall-over for this
     *         catalog. Errors are logged at WARN and surfaced as {@code false}.
     */
    public boolean reloadBundledIfPresent(final String catalog, final String name) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(catalog);
        if (engine == null) {
            return false;
        }
        if (!StaticRuleRegistry.active().find(catalog, name).isPresent()) {
            return false;
        }
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
        perFile.lock();
        try {
            return engine.reloadStatic(catalog, name, alarmResetter, moduleManager);
        } catch (final Throwable t) {
            log.warn("runtime-rule /delete: bundled fall-over reload failed for {}/{}; "
                + "peer tick will retry via gone-keys path", catalog, name, t);
            return false;
        } finally {
            perFile.unlock();
        }
    }

    /**
     * Wildcard-capture helper. Threads {@code bundledContent} through to {@link
     * RuleEngine#dropBackend}: a null value triggers the destructive cascade (drop
     * everything runtime had); a non-null value triggers the delta drop (drop only
     * metrics runtime had that bundled doesn't claim, preserve bundled-shared at
     * matching shape). fullInstall makes the listener chain run.
     */
    private <C extends ApplyContext> void dropBackend(
            final RuleEngine<C> engine, final String catalog, final String name,
            final String runtimeContent, final String bundledContent) {
        final ApplyInputs inputs = new ApplyInputs(
            moduleManager, StorageManipulationOpt.fullInstall(),
            alarmResetter, rules
        );
        final C ctx = engine.newApplyContext(inputs);
        engine.dropBackend(catalog, name, runtimeContent, bundledContent, ctx);
    }
}
