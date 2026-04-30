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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DeltaClassifier;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.LalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.CompiledDSL;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.EngineCompileException;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLScriptKey;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;

/**
 * LAL implementation of {@link RuleEngine}. Owns the {@code (layer, ruleName)} lifecycle for
 * the {@code lal} catalog: parse / classify / compile / register / commit / unregister. There
 * is no backend schema for LAL bundles, so {@link #fireSchemaChanges} and {@link #verify} are
 * no-ops once wired.
 *
 * <p>Holds a stable reference to the scheduler's unified {@code rules} map at construction.
 * Each rule's LAL-applied artifact lives on {@link AppliedRuleScript#getApplied} (an
 * {@link org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied} cast to
 * {@link LalFileApplier.Applied}), so the engine no longer keeps a parallel
 * {@code appliedLal} map.
 *
 * <p>Phase coverage today: {@link #unregister} is wired; the apply phases still throw and the
 * scheduler routes around them via the legacy {@code DSLManager.applyOneRuleFile} path until
 * the per-phase migration completes.
 */
@Slf4j
public final class LalRuleEngine implements RuleEngine<LalApplyContext> {
    private static final Set<String> CATALOGS = Set.of("lal");

    private final Map<String, AppliedRuleScript> rules;
    private final ModuleManager moduleManager;
    /** Lazy-resolved + memoised. {@code LogAnalyzerModule} may not be installed on this OAP;
     *  resolve on first use and degrade to {@code null} on absence. */
    private volatile LalFileApplier lalFileApplier;

    public LalRuleEngine(final Map<String, AppliedRuleScript> rules,
                         final ModuleManager moduleManager) {
        this.rules = rules;
        this.moduleManager = moduleManager;
    }

    /** Read this engine's typed Applied artefact for a key, or {@code null} when there is no
     *  entry / no engine artefact / the entry's artefact belongs to a different engine. */
    private static LalFileApplier.Applied appliedFor(final Map<String, AppliedRuleScript> rules,
                                                     final String key) {
        final AppliedRuleScript script = rules.get(key);
        if (script == null) {
            return null;
        }
        final org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied a = script.getApplied();
        return a instanceof LalFileApplier.Applied ? (LalFileApplier.Applied) a : null;
    }

    /** Resolve the engine's {@link LalFileApplier}. Returns {@code null} when the
     *  {@code LogAnalyzerModule} isn't installed — LAL rules are then no-op and the tick
     *  logs the absence at debug level. */
    private LalFileApplier resolveApplier() {
        LalFileApplier local = lalFileApplier;
        if (local != null) {
            return local;
        }
        try {
            final LogFilterListener.Factory factory = moduleManager.find(LogAnalyzerModule.NAME)
                .provider().getService(LogFilterListener.Factory.class);
            local = new LalFileApplier(factory);
            lalFileApplier = local;
            return local;
        } catch (final Throwable t) {
            return null;
        }
    }

    @Override
    public Set<String> supportedCatalogs() {
        return CATALOGS;
    }

    /**
     * Wraps {@link DeltaClassifier#classifyLal} and folds the {@code isInactive} short-circuit
     * in. LAL's classifier currently only distinguishes NO_CHANGE / NEW / STRUCTURAL — a
     * filter-only path would require a finer parse of expression bodies vs rule keys; falling
     * conservatively to STRUCTURAL is correct (one extra alarm-window reset, no correctness
     * loss).
     */
    @Override
    public Classification classify(final String oldContent, final String newContent, final boolean isInactive) {
        if (isInactive) {
            return Classification.INACTIVE;
        }
        return DeltaClassifier.classifyLal(oldContent, newContent).classification();
    }

    /**
     * The {@code (layer, ruleName)} keys this content claims, encoded as
     * {@code "layer:ruleName"} (auto-layer rules use the literal {@code "auto"}). Used by the
     * cross-file ownership guard.
     */
    @Override
    public Set<String> claimedKeys(final String content, final String sourceName) {
        return DeltaClassifier.enumerateLalRuleKeys(content);
    }

    @Override
    public Set<String> storageImpactKeys(final String priorContent, final String newContent) {
        if (priorContent == null || priorContent.isEmpty()) {
            return Collections.emptySet();
        }
        // LAL: outputType renames + rule add/remove are storage-affecting (they reroute log
        // records to a different storage-backed subclass). DeltaClassifier already enumerates
        // these via lalStorageAffectingChanges.
        return DeltaClassifier.lalStorageAffectingChanges(priorContent, newContent);
    }

    @Override
    public Map<String, Set<String>> activeClaimsExcluding(final String selfKey) {
        final Map<String, Set<String>> out = new HashMap<>();
        for (final Map.Entry<String, AppliedRuleScript> e : rules.entrySet()) {
            if (selfKey.equals(e.getKey())) {
                continue;
            }
            final LalFileApplier.Applied applied = appliedFor(rules, e.getKey());
            if (applied == null) {
                continue;
            }
            final Set<String> claimed = new HashSet<>();
            for (final LalFileApplier.RegisteredRule r : applied.getRegistered()) {
                claimed.add(DSLScriptKey.lalRuleKey(r));
            }
            out.put(e.getKey(), claimed);
        }
        return out;
    }

    @Override
    public boolean recordBundledClaims(final String catalog, final String name, final String content) {
        final String key = DSLScriptKey.key(catalog, name);
        if (appliedFor(rules, key) != null) {
            return false;
        }
        final List<LalFileApplier.RegisteredRule> staticKeys =
            LalFileApplier.parseRuleKeys(content, catalog + "/" + name);
        if (staticKeys.isEmpty()) {
            return false;
        }
        final LalFileApplier.Applied synthetic = new LalFileApplier.Applied(
            catalog + "/" + name, staticKeys);
        rules.compute(key, (k, prev) -> prev == null
            ? new AppliedRuleScript(catalog, name, null, null).withApplied(synthetic)
            : prev.withApplied(synthetic));
        return true;
    }

    @Override
    public LalApplyContext newApplyContext(final ApplyInputs inputs) {
        return new LalApplyContext(inputs);
    }

    /**
     * Compile + register the LAL bundle in one call. {@link LalFileApplier#apply} fuses
     * Javassist class generation with the {@code factory.addOrReplace} dispatcher swap,
     * so by the time compile returns the new (layer, ruleName) keys are live and the old
     * bundle's keys it overwrote are gone — non-overlapping old keys keep serving until
     * commit removes them. The orchestrator runs the cross-file ownership guard before
     * calling this; the engine assumes the planned key set is conflict-free.
     *
     * <p>Throws {@link RuntimeException} wrapping {@link LalFileApplier.ApplyException} on
     * compile / register failure; the orchestrator catches and routes to {@link #rollback}.
     */
    @Override
    public CompiledDSL compile(final RuntimeRuleManagementDAO.RuntimeRuleFile file,
                                  final Classification classification,
                                  final DSLClassLoaderManager.Kind kind,
                                  final LalApplyContext ctx) {
        final String key = DSLScriptKey.key(file.getCatalog(), file.getName());
        final String sourceName = file.getCatalog() + "/" + file.getName();
        final String newHash = ContentHash
            .sha256Hex(file.getContent());
        final LalFileApplier lalApplier = resolveApplier();
        if (lalApplier == null) {
            throw new IllegalStateException(
                "LogAnalyzerModule Factory unavailable for LAL compile of " + sourceName);
        }
        final LalFileApplier.Applied oldApplied = appliedFor(ctx.getRules(), key);
        try {
            final LalFileApplier.Applied newApplied = lalApplier.apply(
                file.getContent(), sourceName, newHash, kind);
            return new CompiledLalDSL(file.getCatalog(), file.getName(), newHash, classification,
                file.getContent(), oldApplied, newApplied);
        } catch (final LalFileApplier.ApplyException ae) {
            // Engine-internal partial rollback for the rare case where Phase 2 of
            // LalFileApplier.apply (the addOrReplace loop) threw after at least one rule was
            // already swapped. Drop those partial entries so the Factory doesn't carry the
            // half-applied set forward. The orchestrator never sees a CompiledLalDSL for this
            // path (we throw EngineCompileException instead of returning), so the orchestrator's
            // rollback() never runs — meaning the old DSL for any overlap key is NOT restored
            // by this catch. The state map still points at the old content, so the next
            // reconciler scan (NO_CHANGE → re-apply on disagreement check) will recover by
            // recompiling the persisted content. Phase 1 failures arrive here with an empty
            // partial set and are no-ops on the Factory.
            if (!ae.getPartial().isEmpty()) {
                lalApplier.remove(new LalFileApplier.Applied(sourceName, ae.getPartial()));
            }
            throw new EngineCompileException(ae);
        }
    }

    /** No-op: LAL has no backend schema. */
    @Override
    public void fireSchemaChanges(final CompiledDSL compiled, final LalApplyContext ctx) {
        // Intentionally no-op. See class-level Javadoc.
    }

    /** No-op: LAL has no backend probe. */
    @Override
    public String verify(final CompiledDSL compiled, final LalApplyContext ctx) {
        return null;
    }

    /**
     * Atomic in-memory swap: compute truly-gone keys (old keys not present in new), drop
     * those from the dispatcher, install the new {@code Applied} in {@code appliedLal[key]},
     * and retire the displaced classloader. {@code addOrReplace} already overwrote
     * overlapping keys at compile time, so commit only needs to clean up keys the new
     * bundle dropped entirely.
     */
    @Override
    public void commit(final CompiledDSL compiled, final LalApplyContext ctx) {
        final CompiledLalDSL c = (CompiledLalDSL) compiled;
        final String key = DSLScriptKey.key(c.getCatalog(), c.getName());
        final String sourceName = c.getCatalog() + "/" + c.getName();
        final LalFileApplier lalApplier = resolveApplier();

        if (c.getOldApplied() != null && lalApplier != null) {
            final Set<String> newKeys = new HashSet<>();
            for (final LalFileApplier.RegisteredRule r : c.getNewApplied().getRegistered()) {
                newKeys.add(DSLScriptKey.lalRuleKey(r));
            }
            final List<LalFileApplier.RegisteredRule> trulyGone = new ArrayList<>();
            for (final LalFileApplier.RegisteredRule r : c.getOldApplied().getRegistered()) {
                if (!newKeys.contains(DSLScriptKey.lalRuleKey(r))) {
                    trulyGone.add(r);
                }
            }
            if (!trulyGone.isEmpty()) {
                lalApplier.remove(new LalFileApplier.Applied(sourceName, trulyGone));
            }
        }
        // Promote the freshly-compiled loader to active. The new loader was minted by
        // applier.apply but never installed in the manager's active map (newBuilder only
        // mints), so a compile failure earlier would have left the prior loader untouched.
        // commit() returns the displaced prior — retire it so the graveyard observes its
        // collection. factory.addOrReplace already swapped the DSL out at compile time and
        // truly-gone keys were just removed above, so the prior is genuinely dead.
        if (c.getNewApplied().getRuleClassLoader() != null) {
            DSLClassLoaderManager.INSTANCE.commit(c.getNewApplied().getRuleClassLoader())
                .filter(prior -> prior != c.getNewApplied().getRuleClassLoader())
                .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
        }
        ctx.getRules().compute(key, (k, prev) -> prev == null
            ? new AppliedRuleScript(c.getCatalog(), c.getName(), null, null)
                .withContentAndApplied(c.getContent(), c.getNewApplied())
            : prev.withContentAndApplied(c.getContent(), c.getNewApplied()));
        log.info("runtime-rule LAL engine: commit OK for {}/{} — {} rule(s) registered",
            c.getCatalog(), c.getName(), c.getNewApplied().getRegistered().size());
    }

    /**
     * Restore the prior live DSL after a failed apply attempt. LAL is unusual among the
     * runtime-rule engines because {@code compile} mutates the global
     * {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener.Factory}
     * via {@code addOrReplace} — the swap is destructive at compile time, not at commit. So
     * after a compile-or-later failure the Factory holds the new DSL while the persistence
     * state-map still claims the old content is the running one. A naive {@code remove(new)}
     * leaves the key empty, the state-map points at the now-evaporated old applied, and the
     * next reconciler scan sees content unchanged → NO_CHANGE → never repairs.
     *
     * <p>The fix is to recompile the prior YAML (read from {@code ctx.getRules()[key]}) and
     * re-register so {@code Factory[key]} ends up with the old DSL again. The prior content
     * was already valid (it's been serving), so recompile is expected to succeed. If somehow
     * it doesn't, log loudly and leave the key empty — the next persistence-state reconcile
     * tick will attempt apply against the persisted content and recover from there.
     */
    @Override
    public void rollback(final CompiledDSL compiled, final LalApplyContext ctx) {
        final CompiledLalDSL c = (CompiledLalDSL) compiled;
        if (c.getNewApplied() == null || c.getNewApplied().getRegistered().isEmpty()) {
            return;
        }
        final LalFileApplier lalApplier = resolveApplier();
        if (lalApplier == null) {
            log.warn("runtime-rule LAL engine: Log Factory unavailable on rollback for {}/{}; "
                + "skipping (next tick retries)", c.getCatalog(), c.getName());
            return;
        }
        final String key = DSLScriptKey.key(c.getCatalog(), c.getName());
        final String sourceName = c.getCatalog() + "/" + c.getName();

        // Step 1: drop the partial new entries. Without this, recompiling the old DSL would
        // hit the cross-file collision guard if any key overlaps.
        lalApplier.remove(new LalFileApplier.Applied(sourceName, c.getNewApplied().getRegistered()));

        // Step 2: restore the old DSL. We need both an old applied (proves there WAS a prior
        // running rule, so the contract is "preserve" not "leave empty") and the YAML content
        // to recompile from (state map). If either is missing, the rollback degenerates to
        // "remove only" and the next reconciler scan picks up.
        if (c.getOldApplied() == null) {
            log.info("runtime-rule LAL engine: rollback OK for {}/{} — {} partial registration(s) removed (no prior DSL to restore)",
                c.getCatalog(), c.getName(), c.getNewApplied().getRegistered().size());
            return;
        }
        final AppliedRuleScript prior = ctx.getRules().get(key);
        if (prior == null || prior.getContent() == null) {
            log.warn("runtime-rule LAL engine: rollback for {}/{} — old applied present but state-map content missing; key left empty, next reconciler scan will retry",
                c.getCatalog(), c.getName());
            return;
        }
        try {
            final String oldHash = ContentHash.sha256Hex(prior.getContent());
            final LalFileApplier.Applied restored = lalApplier.apply(
                prior.getContent(), sourceName, oldHash);
            // Promote the restored loader through the manager so /list reflects the
            // actual serving loader and a later /delete or fall-over can retire it
            // through the graveyard. Without this promote, the manager's active map
            // would still point at the (failed-and-discarded) new loader's prior entry
            // — a stale view the orchestrator never recovers from until the next apply.
            if (restored.getRuleClassLoader() != null) {
                DSLClassLoaderManager.INSTANCE.commit(restored.getRuleClassLoader())
                    .filter(displaced -> displaced != restored.getRuleClassLoader())
                    .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
            }
            ctx.getRules().compute(key, (k, prev) -> prev == null
                ? new AppliedRuleScript(c.getCatalog(), c.getName(),
                    prior.getContent(), null).withApplied(restored)
                : prev.withContentAndApplied(prior.getContent(), restored));
            log.info("runtime-rule LAL engine: rollback OK for {}/{} — {} partial registration(s) removed and prior DSL restored",
                c.getCatalog(), c.getName(), c.getNewApplied().getRegistered().size());
        } catch (final LalFileApplier.ApplyException e) {
            // Pathological: the previously-running content fails to recompile. Could happen
            // if the runtime classpath changed (e.g. SPI provider added/removed). Leave the
            // key empty rather than throw further; the persistence-state retry path is the
            // recovery mechanism.
            log.error("runtime-rule LAL engine: rollback for {}/{} could not restore prior DSL — key left empty; persistence-state retry will reapply",
                c.getCatalog(), c.getName(), e);
        }
    }


    /**
     * Tear down a previously-applied (or static) LAL bundle for {@code (catalog, name)}.
     * Removes the registered rule keys from the LogFilterListener.Factory and retires the
     * per-file classloader. {@code storageOpt} is irrelevant — LAL has no backend.
     *
     * <p><b>Static-rule fallback.</b> When {@code priorLal} is {@code null}, parses
     * {@link StaticRuleRegistry} content for the rule keys and removes those — see the MAL
     * counterpart's class-level Javadoc for the rationale.
     *
     * <p>No alarm reset for LAL — alarm windows are keyed off metric names, not log rules.
     */
    @Override
    public void unregister(final String catalog, final String name, final LalApplyContext ctx) {
        final String key = DSLScriptKey.key(catalog, name);
        final String sourceName = catalog + "/" + name;

        final LalFileApplier.Applied priorLal = appliedFor(ctx.getRules(), key);
        if (priorLal != null) {
            ctx.getRules().computeIfPresent(key, (k, prev) -> prev.withApplied(null));
            final LalFileApplier lalApplier = resolveApplier();
            if (lalApplier == null) {
                log.warn("runtime-rule dslManager: Log Factory unavailable; cannot unregister "
                        + "{} LAL rule(s) for {}/{}",
                    priorLal.getRegistered().size(), catalog, name);
            } else {
                lalApplier.remove(priorLal);
                log.info("runtime-rule dslManager: unregistered {} LAL rule(s) for {}/{}",
                    priorLal.getRegistered().size(), catalog, name);
            }
            DSLClassLoaderManager.INSTANCE.dropRuntime(Catalog.LAL, name);
            return;
        }

        // Static-rule fallback.
        final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
        if (staticContent == null) {
            return;
        }
        final List<LalFileApplier.RegisteredRule> staticKeys =
            LalFileApplier.parseRuleKeys(staticContent, sourceName);
        if (staticKeys.isEmpty()) {
            return;
        }
        final LalFileApplier.Applied synthetic = new LalFileApplier.Applied(sourceName, staticKeys);
        final LalFileApplier lalApplier = resolveApplier();
        if (lalApplier == null) {
            log.warn("runtime-rule dslManager: Log Factory unavailable; cannot unregister "
                    + "{} boot-registered LAL rule(s) for {}/{}",
                staticKeys.size(), catalog, name);
            return;
        }
        lalApplier.remove(synthetic);
        log.info("runtime-rule dslManager: unregistered {} boot-registered LAL rule(s) for "
                + "static rule {}/{}",
            staticKeys.size(), catalog, name);
    }

    /** No-op: LAL has no backend schema, so {@code /delete?mode=revertToBundled} doesn't
     *  need to install prior runtime claims for delta computation — bundled's apply
     *  pipeline reinstalls handlers without needing a runtime delta. */
    @Override
    public void installRuntime(final String catalog, final String name,
                               final String runtimeContent, final LalApplyContext ctx) {
        // Intentionally no-op.
    }

    /**
     * Re-install the bundled LAL rule for {@code (catalog, name)} from {@link StaticRuleRegistry}.
     * The runtime override that masked it has already been removed; without this fall-over,
     * the bundled rule's compiled classes would be gone and operators would have to restart
     * the OAP to get the bundled DSL serving again.
     *
     * <p>Compiles via {@code lalApplier.apply(..., Kind.BUNDLED)} so the per-file loader is
     * minted with the {@code bundled:} prefix — diagnostics can tell at a glance whether a
     * key is being served by a runtime override or a bundled fall-over.
     */
    @Override
    public boolean installBundled(final String catalog, final String name,
                                final Consumer<Set<String>> alarmResetter,
                                final ModuleManager moduleManager,
                                final StorageManipulationOpt storageOpt) {
        // LAL has no backend schema — storageOpt is unused here, accepted for SPI symmetry.
        if (!CATALOGS.contains(catalog)) {
            return false;
        }
        final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
        if (staticContent == null || staticContent.isEmpty()) {
            return false;
        }
        final LalFileApplier lalApplier = resolveApplier();
        if (lalApplier == null) {
            log.warn("runtime-rule LAL engine: Log Factory unavailable; cannot reload static "
                + "rule {}/{} after override removal", catalog, name);
            return false;
        }
        final String sourceName = catalog + "/" + name;
        final String hash = ContentHash.sha256Hex(staticContent);
        try {
            final LalFileApplier.Applied fresh = lalApplier.apply(
                staticContent, sourceName, hash, DSLClassLoaderManager.Kind.BUNDLED);
            // Promote the new bundled: loader. The displaced prior, if any, is retired —
            // typically null here (we're called immediately after unregister, which already
            // dropRuntime'd the old runtime loader).
            if (fresh.getRuleClassLoader() != null) {
                DSLClassLoaderManager.INSTANCE.commit(fresh.getRuleClassLoader())
                    .filter(prior -> prior != fresh.getRuleClassLoader())
                    .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
            }
            // Reset the entry to look like a fresh boot-seeded one: content = bundled YAML
            // (so future classify sees the right priorContent), state = null (so the next
            // gone-keys cleanup correctly skips this as an untouched bundled-only entry),
            // applied = the freshly compiled bundled rule. Without this reset the entry's
            // post-/inactivate state would still be INACTIVE and the next tick's gone-keys
            // path would re-fire teardown + reload in a loop.
            final String key = DSLScriptKey.key(catalog, name);
            rules.compute(key, (k, prev) -> {
                final ReentrantLock lock = prev != null ? prev.getLock() : new ReentrantLock();
                return new AppliedRuleScript(catalog, name, staticContent, null, lock, fresh);
            });
            log.info("runtime-rule LAL engine: bundled fall-over OK for {}/{} — {} rule(s) "
                + "registered from bundled YAML", catalog, name,
                fresh.getRegistered().size());
            return true;
        } catch (final LalFileApplier.ApplyException ae) {
            log.warn("runtime-rule LAL engine: bundled fall-over for {}/{} failed to compile "
                + "the bundled YAML; bundled rule will stay dark until next /addOrUpdate "
                + "or restart", catalog, name, ae);
            return false;
        }
    }
}
