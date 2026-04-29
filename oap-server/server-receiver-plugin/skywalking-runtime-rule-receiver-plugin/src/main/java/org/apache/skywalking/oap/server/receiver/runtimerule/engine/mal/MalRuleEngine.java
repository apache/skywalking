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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine.mal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager;
import org.apache.skywalking.oap.server.core.classloader.RuleClassLoader;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DSLDelta;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DeltaClassifier;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.MalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.CompiledDSL;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.EngineCompileException;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLScriptKey;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * MAL implementation of {@link RuleEngine}. Owns the metric-name lifecycle: parse / classify /
 * compile / register / verify / commit / unregister for {@code otel-rules},
 * {@code log-mal-rules}, and {@code telegraf-rules}. All three catalogs share the same MAL
 * syntax, so one engine handles all three — the catalog name only routes which dispatcher the
 * MAL converter writes into (MeterSystem for otel-rules / telegraf-rules; LAL-extracted MAL
 * for log-mal-rules).
 *
 * <p>Holds a stable reference to the scheduler's unified {@code rules} map at construction.
 * Each rule's MAL-applied artifact lives on {@link AppliedRuleScript#getApplied} (an
 * {@link org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied} cast to
 * {@link MalFileApplier.Applied}), so the engine no longer keeps a parallel
 * {@code appliedMal} map. Each phase call receives a {@link MalApplyContext} that exposes
 * the shared services and the same rules map identity in one cohesive object.
 *
 * <p><b>Phase model.</b> {@link MalFileApplier#apply} fuses compile, register, and listener-
 * chain (BanyanDB define / ES mapping / JDBC table) into one call because the generated
 * Javassist classes register synchronously with the storage listeners. The SPI's {@code
 * fireSchemaChanges} is therefore a no-op for MAL — schema fires inside {@link #compile}.
 * {@link #verify} runs the post-DDL {@code isExists} probe and returns an error string the
 * orchestrator surfaces on the snapshot, or {@code null} on success.
 */
@Slf4j
public final class MalRuleEngine implements RuleEngine<MalApplyContext> {
    private static final Set<String> CATALOGS = Set.of("otel-rules", "log-mal-rules", "telegraf-rules");

    private final Map<String, AppliedRuleScript> rules;
    private final ModuleManager moduleManager;
    /** Lazy-resolved + memoised. {@link MeterSystem} comes from {@code CoreModule}, which
     *  may not be ready when this engine is constructed; resolve on first use. */
    private volatile MalFileApplier malFileApplier;

    public MalRuleEngine(final Map<String, AppliedRuleScript> rules,
                         final ModuleManager moduleManager) {
        this.rules = rules;
        this.moduleManager = moduleManager;
    }

    /** Read this engine's typed Applied artefact for a key, or {@code null} when there is no
     *  entry / no engine artefact / the entry's artefact belongs to a different engine. */
    private static MalFileApplier.Applied appliedFor(final Map<String, AppliedRuleScript> rules,
                                                     final String key) {
        final AppliedRuleScript script = rules.get(key);
        if (script == null) {
            return null;
        }
        final org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied a = script.getApplied();
        return a instanceof MalFileApplier.Applied ? (MalFileApplier.Applied) a : null;
    }

    /** Exposed for {@link org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.StructuralCommitCoordinator}
     *  which needs the same {@code MeterSystem} lookup the engine uses internally. */
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    /** Resolve the engine's {@link MalFileApplier}. Returns {@code null} when {@code CoreModule}
     *  hasn't started yet — caller treats this as a transient pre-boot state. */
    private MalFileApplier resolveApplier() {
        MalFileApplier local = malFileApplier;
        if (local != null) {
            return local;
        }
        try {
            final MeterSystem meterSystem = moduleManager.find(CoreModule.NAME).provider()
                .getService(MeterSystem.class);
            local = new MalFileApplier(meterSystem);
            malFileApplier = local;
            return local;
        } catch (final Throwable t) {
            return null;
        }
    }

    /** Resolve the {@link MalConverterRegistry} for a MAL catalog. {@code null} when the
     *  owning receiver module isn't installed in this OAP — runtime-rule may run without
     *  one of the MAL-consuming receivers, and missing-module degrades to "no push". */
    private MalConverterRegistry resolveConverterRegistry(final String catalog) {
        final String moduleName;
        switch (catalog) {
            case "otel-rules":
                // String literal keeps otel-receiver-plugin out of runtime-rule's pom.
                moduleName = "receiver-otel";
                break;
            case "log-mal-rules":
                moduleName = LogAnalyzerModule.NAME;
                break;
            case "telegraf-rules":
                // String literal keeps telegraf-receiver-plugin out of runtime-rule's pom.
                moduleName = "receiver-telegraf";
                break;
            default:
                return null;
        }
        try {
            return moduleManager.find(moduleName).provider().getService(MalConverterRegistry.class);
        } catch (final Throwable t) {
            log.debug("runtime-rule MAL engine: MalConverterRegistry for catalog {} (module {}) "
                + "not available: {}", catalog, moduleName, t.getMessage());
            return null;
        }
    }

    /** Install / replace this bundle's {@link MetricConvert} in the owning receiver's
     *  registry so ingest samples reach the freshly-compiled converter. No-op when the
     *  convert is null or the owning receiver module isn't installed on this OAP. */
    private void pushRuntimeConverter(final String catalog, final String name,
                                      final MetricConvert convert) {
        if (convert == null) {
            return;
        }
        final MalConverterRegistry registry = resolveConverterRegistry(catalog);
        if (registry == null) {
            return;
        }
        registry.addOrReplaceConverter(runtimeConverterKey(catalog, name), convert);
    }

    /** Reverse of {@link #pushRuntimeConverter}. Invoked from unregister / commit-overwrite
     *  paths so the receiver no longer sees the removed converter. */
    private void dropRuntimeConverter(final String catalog, final String name) {
        final MalConverterRegistry registry = resolveConverterRegistry(catalog);
        if (registry == null) {
            return;
        }
        registry.removeConverter(runtimeConverterKey(catalog, name));
    }

    private static String runtimeConverterKey(final String catalog, final String name) {
        return catalog + ":" + name;
    }

    @Override
    public Set<String> supportedCatalogs() {
        return CATALOGS;
    }

    /**
     * Wraps {@link DeltaClassifier#classifyMal} and folds the {@code isInactive} short-circuit
     * in. The richer {@link DSLDelta} (added / removed / shape-break sets, alarm-reset set)
     * is recomputed during {@link #compile} when needed; keeping the SPI return type to
     * {@link Classification} keeps the scheduler boundary lean.
     */
    @Override
    public Classification classify(final String oldContent, final String newContent, final boolean isInactive) {
        if (isInactive) {
            return Classification.INACTIVE;
        }
        return DeltaClassifier.classifyMal(oldContent, newContent).classification();
    }

    /**
     * Metric names this content claims, used by the cross-file ownership guard. Mirrors the
     * same enumeration MAL apply uses ({@code metricPrefix + "_" + ruleName}). YAML parse
     * failure surfaces as {@link IllegalArgumentException} — the scheduler stamps the apply
     * error and aborts the cross-file check.
     */
    @Override
    public Set<String> claimedKeys(final String content, final String sourceName) {
        return MalFileApplier.parseMetricNames(content, sourceName);
    }

    @Override
    public Set<String> storageImpactKeys(final String priorContent, final String newContent) {
        // First-time bundle: no prior storage identity to break.
        if (priorContent == null || priorContent.isEmpty()) {
            return Collections.emptySet();
        }
        final DSLDelta delta = DeltaClassifier.classifyMal(priorContent, newContent);
        // Only shape-break is guarded — that's the case where existing data on the BanyanDB
        // measure becomes incompatible with the new shape. Add / remove are intentional ops
        // the operator clearly knows about; FILTER_ONLY / NEW don't move any shape.
        if (delta.classification() != Classification.STRUCTURAL) {
            return Collections.emptySet();
        }
        return delta.shapeBreakMetrics();
    }

    @Override
    public Map<String, Set<String>> activeClaimsExcluding(final String selfKey) {
        final Map<String, Set<String>> out = new HashMap<>();
        for (final Map.Entry<String, AppliedRuleScript> e : rules.entrySet()) {
            if (selfKey.equals(e.getKey())) {
                continue;
            }
            final MalFileApplier.Applied applied = appliedFor(rules, e.getKey());
            if (applied == null) {
                continue;
            }
            out.put(e.getKey(), applied.getRegisteredMetricNames());
        }
        return out;
    }

    @Override
    public boolean loadStaticRuleFile(final String catalog, final String name, final String content) {
        final String key = DSLScriptKey.key(catalog, name);
        if (appliedFor(rules, key) != null) {
            return false;
        }
        final Set<String> staticMetricNames =
            MalFileApplier.parseMetricNames(content, catalog + "/" + name);
        if (staticMetricNames.isEmpty()) {
            return false;
        }
        // Synthetic Applied: only the metric-name set is needed for unregister-side cascade.
        // ruleClassLoader is null because static classes live in the default loader; rule and
        // metricConvert are null because this entry tracks boot state, not a runtime apply.
        final MalFileApplier.Applied synthetic = new MalFileApplier.Applied(
            null, null, staticMetricNames, null);
        rules.compute(key, (k, prev) -> prev == null
            ? new AppliedRuleScript(catalog, name, null, null).withApplied(synthetic)
            : prev.withApplied(synthetic));
        return true;
    }

    @Override
    public MalApplyContext newApplyContext(final ApplyInputs inputs) {
        return new MalApplyContext(inputs);
    }

    /**
     * Compile + register + fire schema in one call (the underlying {@link MalFileApplier#apply}
     * is fused — Javassist class generation registers synchronously with the listener chain).
     * Drops shape-break metrics first so {@code MeterSystem.create} can re-register at the new
     * shape. Returns a {@link CompiledMalDSL} carrying the deltas, prior Applied, and the
     * freshly-registered Applied for the rest of the pipeline.
     *
     * <p>Throws {@link MalFileApplier.ApplyException} (wrapped in {@link RuntimeException} for
     * SPI compatibility) on compile / register failure; the orchestrator catches and routes
     * to {@link #rollback}.
     */
    @Override
    public CompiledDSL compile(final RuntimeRuleManagementDAO.RuntimeRuleFile file,
                               final Classification classification,
                               final MalApplyContext ctx) {
        final String key = DSLScriptKey.key(file.getCatalog(), file.getName());
        final String sourceName = file.getCatalog() + "/" + file.getName();
        final String newHash = ContentHash
            .sha256Hex(file.getContent());
        final MalFileApplier applier = resolveApplier();
        if (applier == null) {
            throw new IllegalStateException("MeterSystem unavailable for MAL compile of "
                + sourceName);
        }
        final MalFileApplier.Applied oldApplied = appliedFor(ctx.getRules(), key);

        // FILTER_ONLY fast path: no shape-break drop, no DDL move, no alarm reset, no
        // classloader retire. Just produce the freshly-compiled Applied and let commit do
        // the in-memory swap. The classifier already ran — engines don't re-classify here.
        if (classification == Classification.FILTER_ONLY) {
            final MalFileApplier.Applied fresh;
            try {
                fresh = applier.apply(
                    file.getContent(), sourceName, newHash, ctx.getStorageOpt());
            } catch (final MalFileApplier.ApplyException ae) {
                // Engine-internal partial rollback: undo whatever this attempt managed to
                // register before the throw. Old appliedMal[key] is untouched — it's still
                // serving — so removing the partial set is the only mutation needed.
                applier.remove(ae.getPartiallyRegistered(), ctx.getStorageOpt());
                throw new EngineCompileException(ae);
            }
            return new CompiledMalDSL(file.getCatalog(), file.getName(), newHash, classification,
                file.getContent(), oldApplied, fresh, /* delta */ null, Collections.emptySet());
        }

        // STRUCTURAL / NEW: re-derive the precise delta (added / removed / shape-break) from
        // the prior content. The scheduler's classify() call handed us the verdict but not
        // the delta sets — recomputing here keeps the SPI lean and ensures the delta the
        // engine acts on is internally consistent with the content it's compiling.
        final AppliedRuleScript priorScript = ctx.getRules().get(key);
        final String priorContent = priorScript == null ? null : priorScript.getContent();
        final DSLDelta delta = DeltaClassifier.classifyMal(priorContent, file.getContent());

        // Shape-break metrics MUST be dropped before applier.apply re-registers them at the
        // new shape — MeterSystem.create rejects re-register at a different (function, scope)
        // with an IllegalArgumentException. This IS the destructive shape-break contract:
        // the REST handler's allowStorageChange guardrail has already gated it, and the
        // design accepts that a verify-failure after this point loses shape-break data.
        if (!delta.shapeBreakMetrics().isEmpty()) {
            log.info("runtime-rule MAL engine: {}/{} dropping {} shape-break metric(s) before "
                + "re-create: {}", file.getCatalog(), file.getName(),
                delta.shapeBreakMetrics().size(), delta.shapeBreakMetrics());
            applier.remove(delta.shapeBreakMetrics(), ctx.getStorageOpt());
        }

        final MalFileApplier.Applied newApplied;
        try {
            newApplied = applier.apply(
                file.getContent(), sourceName, newHash, ctx.getStorageOpt());
        } catch (final MalFileApplier.ApplyException ae) {
            // Engine-internal partial rollback: undo only the metrics this attempt would
            // have created or re-shaped (added ∪ shape-break). Unchanged metrics short-
            // circuit on MeterSystem idempotency and were never re-registered, so removing
            // them would wipe BanyanDB measure data the apply never actually touched.
            // Shape-break metrics: we removed the old class pre-apply; the new one may or
            // may not have registered before the throw — remove is idempotent either way.
            // That's the documented allowStorageChange cost.
            final Set<String> rollbackTargets = new HashSet<>();
            rollbackTargets.addAll(delta.addedMetrics());
            rollbackTargets.addAll(delta.shapeBreakMetrics());
            applier.remove(rollbackTargets, ctx.getStorageOpt());
            throw new EngineCompileException(ae);
        }

        final Set<String> addedPlusShapeBreak = new HashSet<>();
        addedPlusShapeBreak.addAll(delta.addedMetrics());
        addedPlusShapeBreak.addAll(delta.shapeBreakMetrics());

        return new CompiledMalDSL(file.getCatalog(), file.getName(), newHash, classification,
            file.getContent(), oldApplied, newApplied, delta,
            Collections.unmodifiableSet(addedPlusShapeBreak));
    }

    /**
     * No-op for MAL — schema changes fire inside {@link #compile} via {@link
     * MalFileApplier#apply}, which drives the listener chain synchronously with class
     * registration. Kept as an SPI hook for future engines (e.g. OAL) where compile and
     * fire-schema can genuinely be separated.
     */
    @Override
    public void fireSchemaChanges(final CompiledDSL compiled, final MalApplyContext ctx) {
        // Intentionally no-op. See class-level Javadoc.
    }

    /**
     * Post-DDL {@code isExists} probe. Returns {@code null} on success, an error string on
     * mismatch the orchestrator stamps on the snapshot's {@code applyError}. Only verifies
     * added + shape-break metrics — verifying unchanged metrics would duplicate the startup-
     * time isExists check and burn gRPC round-trips for no benefit.
     *
     * <p>Gracefully degrades when storage / model services aren't present (early boot, some
     * embedded test topologies): logs DEBUG and returns {@code null}.
     */
    @Override
    public String verify(final CompiledDSL compiled, final MalApplyContext ctx) {
        final CompiledMalDSL c = (CompiledMalDSL) compiled;
        if (c.getClassification() == Classification.FILTER_ONLY) {
            return null;
        }
        final Set<String> targets = c.getAddedPlusShapeBreak();
        if (targets.isEmpty()) {
            return null;
        }
        final ModelInstaller installer;
        final IModelManager modelManager;
        try {
            installer = ctx.getModuleManager().find(StorageModule.NAME).provider()
                .getService(ModelInstaller.class);
            modelManager = ctx.getModuleManager().find(CoreModule.NAME).provider()
                .getService(IModelManager.class);
        } catch (final Throwable t) {
            log.debug("runtime-rule MAL engine: post-apply verify skipped for {}/{} "
                + "(storage/model services unavailable: {})",
                c.getCatalog(), c.getName(), t.getMessage());
            return null;
        }
        final List<String> failures = new ArrayList<>();
        for (final Model m : modelManager.allModels()) {
            if (!targets.contains(m.getName())) {
                continue;
            }
            try {
                final ModelInstaller.InstallInfo info = installer.isExists(m, ctx.getStorageOpt());
                if (!info.isAllExist()) {
                    failures.add(info.buildInstallInfoMsg());
                }
            } catch (final Throwable t) {
                failures.add(m.getName() + " (" + m.getDownsampling() + "): " + t.getMessage());
            }
        }
        if (failures.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("runtime-rule MAL engine: post-apply verify OK for {}/{} ({} metric(s))",
                    c.getCatalog(), c.getName(), targets.size());
            }
            return null;
        }
        final String msg = "post-apply isExists verify FAILED: " + String.join("; ", failures);
        log.error("runtime-rule MAL engine CRITICAL: {}/{} {} — orchestrator will roll back to "
            + "prior bundle. Fix the DSL/storage mismatch and re-push.",
            c.getCatalog(), c.getName(), msg);
        return msg;
    }

    /**
     * Atomic in-memory swap: install the new {@code Applied} in {@code appliedMal[key]},
     * publish the freshly-compiled {@link org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert}
     * to the owning receiver's converter registry, retire the displaced classloader (STRUCTURAL
     * / NEW only — FILTER_ONLY's old loader's Metrics classes are still the live storage
     * target so it stays), and drive alarm-window reset for affected metric names.
     *
     * <p>Idempotent at the in-memory level: re-applying the same Applied is a no-op except for
     * a redundant alarm reset. The orchestrator owns the snapshot transition (
     * {@link org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState}) and
     * persistence — engine commit only mutates engine-owned state.
     */
    @Override
    public void commit(final CompiledDSL compiled, final MalApplyContext ctx) {
        final CompiledMalDSL c = (CompiledMalDSL) compiled;
        // Drop metrics this bundle no longer claims (STRUCTURAL/NEW only — FILTER_ONLY has
        // identical metric sets). Honours the caller's storage opt: a peer-driven tick uses
        // localCacheOnly here so the cluster-shared backend isn't touched; the main's REST
        // path uses fullInstall to fire dropTable through the listener chain. Must run
        // BEFORE the swap so the about-to-be-displaced applier still owns the prototypes.
        if (c.getClassification() != Classification.FILTER_ONLY
                && c.getDelta() != null
                && !c.getDelta().removedMetrics().isEmpty()) {
            final MalFileApplier applier = resolveApplier();
            if (applier != null) {
                applier.remove(c.getDelta().removedMetrics(), ctx.getStorageOpt());
            }
        }
        final String commitKey = DSLScriptKey.key(c.getCatalog(), c.getName());
        ctx.getRules().compute(commitKey, (k, prev) -> prev == null
            ? new AppliedRuleScript(c.getCatalog(), c.getName(), null, null)
                .withContentAndApplied(c.getContent(), c.getNewApplied())
            : prev.withContentAndApplied(c.getContent(), c.getNewApplied()));
        pushRuntimeConverter(
            c.getCatalog(), c.getName(), c.getNewApplied().getMetricConvert());
        // Promote the freshly-compiled loader to active. newBuilder only mints; commit() is
        // the only path that registers in the manager's active map, so a compile failure
        // earlier would have left the prior loader untouched. STRUCTURAL / NEW commit retires
        // the displaced prior — its Metrics classes have been replaced via MeterSystem
        // re-create, so it's truly dead. FILTER_ONLY does NOT retire — its Metrics classes
        // are still the storage target via MeterSystem.meterPrototypes, and the new loader's
        // MalExpression bridges to the same prototypes. Both old and new loaders coexist;
        // the manager's active slot points at the newest, the older is held strong via
        // meterPrototypes and naturally GC'd if a later STRUCTURAL ever displaces it.
        if (c.getNewApplied().getRuleClassLoader() != null) {
            final Optional<RuleClassLoader> prior =
                DSLClassLoaderManager.INSTANCE.commit(c.getNewApplied().getRuleClassLoader());
            if (c.getClassification() != Classification.FILTER_ONLY) {
                prior.filter(p -> p != c.getNewApplied().getRuleClassLoader())
                     .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
            }
        }
        if (c.getClassification() != Classification.FILTER_ONLY) {
            // Alarm reset for metrics whose semantics changed (added / removed / shape-break).
            if (c.getDelta() != null && !c.getDelta().alarmResetSet().isEmpty()) {
                ctx.getAlarmResetter().accept(c.getDelta().alarmResetSet());
            }
        }
        log.info("runtime-rule MAL engine: commit OK for {}/{} — {} metric(s) registered ({})",
            c.getCatalog(), c.getName(),
            c.getNewApplied().getRegisteredMetricNames().size(), c.getClassification());
    }

    /**
     * Drop registrations from THIS attempt — the just-attempted added + shape-break metrics.
     * Old Applied stays in {@code appliedMal[key]} so unchanged metrics keep serving (the
     * orchestrator hasn't called commit yet, so the swap hasn't happened). Idempotent.
     *
     * <p><b>Shape-break cost.</b> Pre-compile we removed the old shape-break classes; if the
     * new ones never registered (compile threw before reaching them), the metrics are gone
     * for this evaluation period — documented cost of the {@code allowStorageChange=true}
     * guardrail. Once the operator pushes a fixed version, the next apply re-registers.
     */
    @Override
    public void rollback(final CompiledDSL compiled, final MalApplyContext ctx) {
        final CompiledMalDSL c = (CompiledMalDSL) compiled;
        if (c.getAddedPlusShapeBreak().isEmpty()) {
            return;
        }
        final MalFileApplier applier = resolveApplier();
        if (applier == null) {
            log.warn("runtime-rule MAL engine: MeterSystem unavailable on rollback for {}/{}; "
                + "skipping (next tick retries)", c.getCatalog(), c.getName());
            return;
        }
        applier.remove(c.getAddedPlusShapeBreak(), ctx.getStorageOpt());
        log.info("runtime-rule MAL engine: rollback OK for {}/{} — {} metric(s) removed",
            c.getCatalog(), c.getName(), c.getAddedPlusShapeBreak().size());
    }

    /**
     * Tear down a previously-applied (or static) MAL bundle for {@code (catalog, name)}.
     * <p>The {@link MalApplyContext#getStorageOpt()} parameter decides whether the listener
     * chain reaches the backend:
     * <ul>
     *   <li>{@code localCacheOnly} — soft-pause path. Local state is cleared (meterPrototypes,
     *       Models from registry, appliedMal entry, classloader retired) but the listener's
     *       {@code dropTable} is skipped, so the BanyanDB measure / ES index / JDBC table stays
     *       intact. This is the {@code /inactivate} contract.</li>
     *   <li>{@code fullInstall} — destructive path. Same local cleanup PLUS the listener fires
     *       {@code dropTable} so the backend resource is removed. This is the {@code /delete}
     *       contract and the tick's gone-keys cleanup on main.</li>
     * </ul>
     *
     * <p><b>Cascade-first ordering.</b> {@code applier.remove} runs before {@code
     * appliedMal.remove(key)}. A backend-drop throw therefore leaves {@code appliedMal[key]}
     * populated so the next tick (or operator retry) re-enters this method and re-fires the
     * cascade. Listeners are required to be idempotent on the drop ({@code BanyanDB
     * delete-measure} on a non-existent measure is a no-op).
     *
     * <p><b>Static-rule fallback.</b> When {@code priorMal} is {@code null} (this rule never
     * had a runtime apply on this node — e.g., a static-only rule receiving its first
     * {@code /inactivate}, or a static-shadow tombstone reaching a fresh main) the method
     * parses {@link StaticRuleRegistry} content for the metric names and removes those
     * directly. {@code MeterSystem.removeMetric} short-circuits when the prototype is already
     * gone, so this fallback is correct under both opt modes.
     *
     * <p><b>Alarm reset.</b> The orchestrator decides whether to invoke the alarm kernel by
     * supplying either the real {@link MalApplyContext#getAlarmResetter()} (full tear-down)
     * or a no-op (update path where the caller will drive the reset itself with the precise
     * delta).
     */
    @Override
    public void unregister(final String catalog, final String name, final MalApplyContext ctx) {
        final String key = DSLScriptKey.key(catalog, name);
        final String sourceName = catalog + "/" + name;

        // Always drop the MalConverterRegistry entry for this (catalog, name). The key
        // namespace is shared between boot-time and runtime converters, so this single call
        // covers both cases; no-op for absent keys. Outside the priorMal guard so the first
        // /inactivate of a static-only rule successfully drops the boot-time converter even
        // though no Applied entry ever existed.
        dropRuntimeConverter(catalog, name);

        final MalFileApplier.Applied priorMal = appliedFor(ctx.getRules(), key);
        if (priorMal != null) {
            final MalFileApplier applier = resolveApplier();
            if (applier == null) {
                log.warn("runtime-rule MAL engine: MeterSystem unavailable; cannot unregister "
                        + "{} metric(s) for {}/{}",
                    priorMal.getRegisteredMetricNames().size(), catalog, name);
                return;
            }
            applier.remove(priorMal.getRegisteredMetricNames(), ctx.getStorageOpt());
            ctx.getRules().computeIfPresent(key, (k, prev) -> prev.withApplied(null));
            log.info("runtime-rule MAL engine: unregistered {} metric(s) for {}/{}",
                priorMal.getRegisteredMetricNames().size(), catalog, name);
            DSLClassLoaderManager.INSTANCE.dropRuntime(Catalog.of(catalog), name);
            ctx.getAlarmResetter().accept(priorMal.getRegisteredMetricNames());
            return;
        }

        // Static-rule fallback.
        final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
        if (staticContent == null) {
            return;
        }
        final Set<String> staticMetricNames = MalFileApplier.parseMetricNames(staticContent, sourceName);
        if (staticMetricNames.isEmpty()) {
            return;
        }
        final MalFileApplier applier = resolveApplier();
        if (applier == null) {
            log.warn("runtime-rule MAL engine: MeterSystem unavailable; cannot unregister "
                    + "{} boot-registered metric(s) for {}/{}",
                staticMetricNames.size(), catalog, name);
            return;
        }
        applier.remove(staticMetricNames, ctx.getStorageOpt());
        log.info("runtime-rule MAL engine: unregistered {} boot-registered metric(s) for "
                + "static rule {}/{}",
            staticMetricNames.size(), catalog, name);
        ctx.getAlarmResetter().accept(staticMetricNames);
    }

    /**
     * Discharge backend schema for {@code /delete}. {@code bundledContent} controls the
     * destructiveness:
     *
     * <ul>
     *   <li><b>{@code null}</b> — destructive: re-register prototypes locally under
     *       {@code localCacheOnly} (so the listener chain doesn't re-create the measure
     *       we're about to drop) and then tear down via {@link #unregister} under
     *       {@code fullInstall}. The two-step dance is needed because {@code /inactivate}
     *       has already cleared {@code appliedMal[key]}; without re-register, unregister
     *       would no-op the cascade and the backend would orphan.</li>
     *   <li><b>non-null</b> — delta: classify {@code runtimeContent} → {@code bundledContent}
     *       and drop only metrics the runtime row claims that bundled does NOT claim, plus
     *       metrics in both at different shape. Bundled-shared metrics at matching shape
     *       are preserved (no data loss for the measures bundled will reuse on its
     *       synchronous reload). The drop runs under {@code fullInstall} so the listener
     *       cascade fires.</li>
     * </ul>
     *
     * <p>Throws {@link IllegalStateException} on MeterSystem unavailability or re-register
     * failure; the caller propagates so the REST handler aborts {@code dao.delete}.
     */
    @Override
    public void dropBackend(final String catalog, final String name,
                            final String runtimeContent, final String bundledContent,
                            final MalApplyContext ctx) {
        final MalFileApplier applier = resolveApplier();
        if (applier == null) {
            throw new IllegalStateException(
                "MeterSystem unavailable; cannot drop backend measure for " + catalog + "/"
                    + name + " — refusing to delete the row and orphan the measure. Retry "
                    + "when MeterSystem is up.");
        }
        if (bundledContent != null) {
            dropBackendDelta(catalog, name, runtimeContent, bundledContent, applier);
            return;
        }
        dropBackendDestructive(catalog, name, runtimeContent, applier, ctx);
    }

    private void dropBackendDelta(final String catalog, final String name,
                                  final String runtimeContent, final String bundledContent,
                                  final MalFileApplier applier) {
        final DSLDelta delta = DeltaClassifier.classifyMal(runtimeContent, bundledContent);
        final Set<String> toDrop = new HashSet<>();
        toDrop.addAll(delta.removedMetrics());
        toDrop.addAll(delta.shapeBreakMetrics());
        if (toDrop.isEmpty()) {
            log.info("runtime-rule MAL engine: /delete bundled-twin delta empty for {}/{} — "
                + "nothing to drop, bundled will reuse all existing measures",
                catalog, name);
            return;
        }
        log.info("runtime-rule MAL engine: /delete bundled-twin delta for {}/{} — dropping {} "
            + "runtime-only / shape-break metric(s): {}",
            catalog, name, toDrop.size(), toDrop);
        applier.remove(toDrop, StorageManipulationOpt.fullInstall());
    }

    private void dropBackendDestructive(final String catalog, final String name,
                                        final String runtimeContent, final MalFileApplier applier,
                                        final MalApplyContext ctx) {
        final String key = DSLScriptKey.key(catalog, name);
        final String sourceName = catalog + "/" + name;
        final String hash = ContentHash.sha256Hex(runtimeContent);

        // Re-register prototypes locally so unregister has Models + meterPrototypes to walk.
        // localCacheOnly suppresses listener-side backend define — we don't want to recreate
        // the measure we're about to drop.
        try {
            final MalFileApplier.Applied applied = applier.apply(
                runtimeContent, sourceName, hash, StorageManipulationOpt.localCacheOnly());
            if (applied.getRuleClassLoader() != null) {
                DSLClassLoaderManager.INSTANCE.commit(applied.getRuleClassLoader())
                    .filter(prior -> prior != applied.getRuleClassLoader())
                    .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
            }
            ctx.getRules().compute(key, (k, prev) -> prev == null
                ? new AppliedRuleScript(catalog, name, null, null)
                    .withContentAndApplied(runtimeContent, applied)
                : prev.withContentAndApplied(runtimeContent, applied));
        } catch (final MalFileApplier.ApplyException ae) {
            // Roll back any partial state that DID land before the throw — every other apply
            // path does the same. localCacheOnly matches the apply: backend was untouched.
            if (ae.getPartiallyRegistered() != null && !ae.getPartiallyRegistered().isEmpty()) {
                try {
                    applier.remove(ae.getPartiallyRegistered(),
                        StorageManipulationOpt.localCacheOnly());
                } catch (final Throwable rollbackErr) {
                    log.warn("runtime-rule /delete: rollback of partial re-register also "
                        + "failed for {}/{}; {} prototype(s) may persist locally until OAP "
                        + "restart.", catalog, name, ae.getPartiallyRegistered().size(),
                        rollbackErr);
                }
            }
            throw new IllegalStateException(
                "re-register for backend drop failed for " + catalog + "/" + name
                    + "; refusing to delete the row to avoid orphaning the measure. "
                    + "Cause: " + ae.getMessage(), ae);
        }

        // Tear down with fullInstall: drops backend (listener whenRemoving fires dropTable
        // for each downsampling variant) and clears the re-registered local state. We need
        // to swap the storage opt for this call — clone the context with fullInstall.
        final MalApplyContext fullInstallCtx = withStorageOpt(ctx, StorageManipulationOpt.fullInstall());
        unregister(catalog, name, fullInstallCtx);
    }

    /** Clone {@code ctx} with the given storage opt. Used by the destructive
     *  {@link #dropBackend} path to flip from {@code localCacheOnly} (re-register) to
     *  {@code fullInstall} (destructive teardown). */
    private static MalApplyContext withStorageOpt(final MalApplyContext ctx,
                                                  final StorageManipulationOpt opt) {
        final ApplyInputs inputs = new ApplyInputs(
            ctx.getModuleManager(), opt,
            ctx.getAlarmResetter(), ctx.getRules());
        return new MalApplyContext(inputs);
    }

    /**
     * Re-install the bundled MAL rule for {@code (catalog, name)} from {@link StaticRuleRegistry}.
     * The runtime override that masked it has already been removed; without this fall-over,
     * the bundled rule's MeterSystem prototypes / Metrics classes / MetricConvert would be
     * gone and operators would have to restart the OAP to get the bundled metrics flowing
     * again.
     *
     * <p>Compiles via {@code applier.apply(..., Kind.STATIC)} so the per-file loader is minted
     * with the {@code static:} prefix — diagnostics can tell at a glance whether a key is
     * being served by a runtime override or a static fall-over. The applier internally runs
     * under {@code localCacheOnly}: the bundled metric backend already exists (it pre-dates
     * the override), so we only need to re-register local prototypes and re-publish the
     * MetricConvert.
     *
     * <p>Alarm reset is forwarded for the bundled metric set so dispatch resumes against a
     * clean window.
     */
    @Override
    public boolean reloadStatic(final String catalog, final String name,
                                final Consumer<Set<String>> alarmResetter,
                                final ModuleManager moduleManager) {
        if (!CATALOGS.contains(catalog)) {
            return false;
        }
        final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
        if (staticContent == null || staticContent.isEmpty()) {
            return false;
        }
        final MalFileApplier applier = resolveApplier();
        if (applier == null) {
            log.warn("runtime-rule MAL engine: MeterSystem unavailable; cannot reload static "
                + "rule {}/{} after override removal", catalog, name);
            return false;
        }
        final String sourceName = catalog + "/" + name;
        final String hash = ContentHash.sha256Hex(staticContent);
        try {
            // createIfAbsent rather than localCacheOnly: when reload follows a /delete that
            // dropped runtime-only / shape-break measures (via dropBundledTwinDelta), some
            // bundled-claimed measures may be missing in the backend. createIfAbsent recreates
            // them without affecting backends that already match.
            final MalFileApplier.Applied fresh = applier.apply(
                staticContent, sourceName, hash,
                StorageManipulationOpt.createIfAbsent(),
                DSLClassLoaderManager.Kind.STATIC);
            // Promote the new static: loader. Any prior loader (typically null — unregister
            // already dropRuntime'd it) is retired so the graveyard observes its collection.
            if (fresh.getRuleClassLoader() != null) {
                DSLClassLoaderManager.INSTANCE.commit(fresh.getRuleClassLoader())
                    .filter(prior -> prior != fresh.getRuleClassLoader())
                    .ifPresent(DSLClassLoaderManager.INSTANCE::retire);
            }
            // Reset the entry to look like a fresh boot-seeded one: state = null so the
            // next gone-keys pass correctly skips this as an untouched bundled-only entry
            // (otherwise the post-/inactivate INACTIVE state would leak across and the
            // tick would re-fire teardown + reload in a loop).
            final String key = DSLScriptKey.key(catalog, name);
            rules.compute(key, (k, prev) -> {
                final ReentrantLock lock = prev != null ? prev.getLock() : new ReentrantLock();
                return new AppliedRuleScript(catalog, name, staticContent, null, lock, fresh);
            });
            pushRuntimeConverter(catalog, name, fresh.getMetricConvert());
            alarmResetter.accept(fresh.getRegisteredMetricNames());
            log.info("runtime-rule MAL engine: static fall-over OK for {}/{} — {} metric(s) "
                + "re-registered from bundled YAML", catalog, name,
                fresh.getRegisteredMetricNames().size());
            return true;
        } catch (final MalFileApplier.ApplyException ae) {
            log.warn("runtime-rule MAL engine: static fall-over for {}/{} failed to compile "
                + "the bundled YAML; bundled metrics will stay dark until next /addOrUpdate "
                + "or restart", catalog, name, ae);
            return false;
        }
    }
}
