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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Per-DSL phase pipeline. The scheduler (DSLManager) holds one engine per DSL via
 * {@link RuleEngineRegistry} and drives every engine through three orchestrators:
 * {@code DSLRuntimeApply} (apply pipeline), {@code DSLRuntimeUnregister} (tear-down),
 * {@code DSLRuntimeDelete} (destructive). The scheduler's apply driver is unified
 * ({@code DSLManager.handleApply}) — there is no per-DSL branching. Engines own all
 * DSL-specific work: Javassist generation, applier registration, backend listener chain,
 * classloader retire, alarm-reset target sets, backend service lookup.
 *
 * <h2>How each method is scheduled</h2>
 *
 * <p><b>1. Read-only inputs the scheduler queries before/around the pipeline</b>
 * <pre>
 *   supportedCatalogs() — read once at module start by {@link RuleEngineRegistry#register}.
 *                         Lets the registry build catalog → engine in O(1).
 *
 *   classify(old, new, inactive) — called by {@code DSLManager.handleApply} on every apply
 *                         attempt. Drives routing:
 *                           INACTIVE → {@code DSLRuntimeUnregister} + tombstone snapshot
 *                           NO_CHANGE → snapshot hash refresh, no engine work
 *                           NEW / FILTER_ONLY / STRUCTURAL → continue to ownership guard
 *                                                            then compile
 *
 *   claimedKeys(content, source) — called by the cross-file ownership guard. Engine
 *                         returns its file's claim set; scheduler intersects against
 *                         {@link #activeClaimsExcluding} of every other live bundle plus
 *                         INACTIVE-row claims read from the DAO.
 *
 *   activeClaimsExcluding(selfKey) — called by the cross-file ownership guard. Engine
 *                         walks its internal applied-state map and returns owner-keyed
 *                         claim sets so the guard reports which file holds a colliding
 *                         claim in its error message.
 *
 *   storageImpactKeys(prior, new) — called by the REST {@code allowStorageChange}
 *                         guardrail. Engine returns the per-DSL set of changes that
 *                         mutate cluster-shared backend schema (MAL: shape-break metrics
 *                         plus added / removed names; LAL: outputType renames + rule key
 *                         add/remove). Empty for body-only edits, which the guardrail
 *                         allows unconditionally.
 * </pre>
 *
 * <p><b>2. Apply pipeline — every classification flows through the same path</b>
 * <pre>
 *   The scheduler always invokes {@code DSLRuntimeApply#compileAndVerify} and only then
 *   decides to stash (deferCommit, REST 2-PC path) vs commitInline (tick / sync paths).
 *   FILTER_ONLY and STRUCTURAL share this flow — engine.commit dispatches on classification
 *   internally for the bits that differ (FILTER_ONLY skips classloader retire + alarm
 *   reset + removedMetrics drop because shapes are identical).
 *
 *   newApplyContext(inputs)
 *     ↓                        engine narrows the shared {@link ApplyInputs} into its own
 *                              context subtype, folding in DSL-specific state.
 *
 *   compile(file, classification, ctx)
 *     ↓                        Generate classes + register handlers + (for MAL) fire the
 *                              backend listener chain. The engine internally rolls back
 *                              its partial state on failure before throwing
 *                              {@link EngineCompileException} — by the time the throw
 *                              reaches the orchestrator, no engine-side leftovers remain.
 *
 *   fireSchemaChanges(compiled, ctx)
 *     ↓                        SPI hook for engines whose listener chain isn't fused with
 *                              compile. MAL: no-op (fired inside compile). LAL: no-op (no
 *                              backend schema). Future engines may use this.
 *
 *   verify(compiled, ctx)
 *     ↓                        Post-DDL probe. MAL: isExists round-trip per Model. LAL:
 *                              no-op (returns null). Returns null on success or an error
 *                              string the orchestrator stamps on the snapshot.
 *
 *     ┌─ verify-failed ──→ rollback(compiled, ctx) — engine drops just-registered metrics;
 *     │                                              old applied state still serves.
 *     └─ verify-OK    ──→ outcome.status = READY_TO_COMMIT, returned to scheduler:
 *                          ├─ deferCommit → commitCoord.stash (REST 2-PC; drained on
 *                          │                                    persist outcome)
 *                          └─ inline      → commitCoord.commitInline → engine.commit
 *
 *   commit(compiled, ctx)      Drop removedMetrics from the dispatcher, swap the
 *                              engine-applied artefacts + appliedContent, push the freshly-
 *                              compiled converter to the owning receiver, retire the
 *                              displaced classloader (non-FILTER_ONLY only), fire alarm
 *                              reset for affected metric names. Idempotent at the in-memory
 *                              level.
 *
 *   rollback(compiled, ctx)    Drop registrations from THIS attempt only — the just-
 *                              registered added + shape-break metrics. Old applied state
 *                              is intact (commit hasn't run), so unchanged metrics keep
 *                              serving.
 * </pre>
 *
 * <p><b>3. Tear-down (driven by {@code DSLRuntimeUnregister})</b>
 * <pre>
 *   unregister(catalog, name, ctx) — called for INACTIVE classification, the tick's gone-
 *                         keys cleanup, and any other path that needs to drop a bundle.
 *                         Engine clears its applied-state entry, drops registered
 *                         dispatcher handlers, retires the classloader, fires alarm reset
 *                         for the prior metric set. Storage opt determines whether
 *                         backend schema is dropped (fullInstall) or preserved
 *                         (localCacheOnly — the {@code /inactivate} contract).
 * </pre>
 *
 * <p><b>4. Destructive {@code /delete} (driven by {@code DSLRuntimeDelete})</b>
 * <pre>
 *   dropBackend(catalog, name, content, ctx) — called by REST {@code /delete}
 *                         after {@code /inactivate} has already cleared the engine's
 *                         applied state. Engines with backend schema (MAL) re-register
 *                         prototypes locally then tear down under fullInstall so the
 *                         listener chain runs the destructive cascade. Engines without
 *                         backend (LAL) implement as no-op — the DAO row deletion alone
 *                         discharges the rule.
 * </pre>
 *
 * <p><b>5. Boot / recovery (driven by {@code StaticRuleLoader})</b>
 * <pre>
 *   loadStaticRuleFile(catalog, name, content) — called once at boot for every static rule
 *                         the catalog loaders compiled at module start, and again on each
 *                         tick for any static rule whose DB row got {@code /delete}d while
 *                         the disk content remained. Engine seeds a synthetic applied
 *                         entry with its per-DSL claim set so the next {@code /inactivate}
 *                         / {@code /addOrUpdate} / Suspend lookup finds the bundle.
 * </pre>
 *
 * <h2>Boundary contract</h2>
 *
 * <p>Engines own everything DSL-specific: delta classifier, compiler, dispatcher
 * (MeterSystem / LogFilterListener / ...) registration, backend service lookup, applied-
 * state map, classloader handling, alarm-reset target derivation, the {@link CompiledDSL}
 * subclass that carries per-call state, and the {@link ApplyContext} subtype that carries
 * the scheduler-provided + engine-internal state per phase.
 *
 * <p>The scheduler owns everything DSL-agnostic: lock acquisition, cluster Suspend/Resume
 * RPCs, persistence (DAO upsert), ddl-debt marker bookkeeping, cross-file ownership
 * enforcement (parameterised by {@link #claimedKeys} + {@link #activeClaimsExcluding}),
 * self-heal, tick scheduling, classloader graveyard, alarm-reset dispatch, snapshot
 * transitions, and the 2-PC stash for deferred commits. It interacts with engines only
 * through this SPI + the three orchestrators above + {@code StaticRuleLoader}.
 *
 * <h2>Adding a new DSL</h2>
 *
 * <p>Implement {@code RuleEngine<MyApplyContext>} and the SPI methods, declare your
 * catalogs in {@link #supportedCatalogs}, build a concrete {@code MyApplyContext} subtype
 * carrying any extra DSL state, register the engine with {@link RuleEngineRegistry} at
 * module start. No scheduler edit is required — the unified {@code handleApply} routes
 * via the registry. The boundary holds for telegraf-rules (already MAL syntax, so just an
 * additional entry in {@code MalRuleEngine.supportedCatalogs}) and OAL (would be its own
 * engine + context).
 *
 * @param <C> the concrete {@link ApplyContext} subtype this engine consumes; bound at the
 *            class level so the orchestrators' dispatch helpers are type-safe end-to-end.
 */
public interface RuleEngine<C extends ApplyContext> {
    /**
     * Catalogs this engine handles, e.g. {@code {"otel-rules", "log-mal-rules",
     * "telegraf-rules"}} for the MAL engine, {@code {"lal"}} for the LAL engine.
     * {@link RuleEngineRegistry} reads this once at registration time.
     */
    Set<String> supportedCatalogs();

    /**
     * Pure function. Compares {@code newContent} against the previous successfully-applied
     * content for the same key (or {@code null} if no prior bundle) plus the row status,
     * and returns the {@link Classification} the scheduler uses to drive the rest of the
     * pipeline. The {@code isInactive} flag short-circuits to {@link Classification#INACTIVE}.
     */
    Classification classify(String oldContent, String newContent, boolean isInactive);

    /**
     * Pure function. Returns the names this content claims for cross-file ownership
     * comparison: metric names for MAL, {@code "<layer>:<ruleName>"} encoded keys for LAL.
     * The scheduler runs the comparison itself (active appliedX entries plus INACTIVE rows
     * from the DAO); the engine just produces its file's claim set.
     */
    Set<String> claimedKeys(String content, String sourceName);

    /**
     * Storage-affecting subset of a content change. The REST {@code allowStorageChange}
     * guardrail uses this to refuse edits that would mutate cluster-shared backend schema
     * (BanyanDB measure shape, ES index mapping, JDBC table) unless the operator explicitly
     * opted in.
     *
     * <p>MAL: shape-break metric names (function or scope changed) plus added / removed
     * names — those reach the listener chain on the next apply.
     *
     * <p>LAL: rule-name additions / removals plus {@code outputType} renames — those reroute
     * log records to a different storage-backed subclass.
     *
     * <p>Empty result when the change is body-only (filter / tag / output-field tweaks) —
     * those don't touch storage and the guardrail allows them through unconditionally.
     *
     * <p>Throws {@link IllegalArgumentException} if either content is unparseable; the REST
     * handler turns the throw into a 400 {@code compile_failed} response.
     */
    Set<String> storageImpactKeys(String priorContent, String newContent);

    /**
     * Active claims by every other live bundle this engine knows about, excluding
     * {@code selfKey}. Returned as a map of {@code "catalog:name"} → claimed keys for that
     * bundle. The orchestrator's cross-file ownership guard intersects each entry's value
     * against the planned key set to detect collisions; surfacing per-owner sets (rather
     * than a flat union) lets the guard report which file holds the colliding name in its
     * error message.
     *
     * <p>Engines read their own internal {@code appliedX} map (the one their context
     * exposes); the orchestrator does not need access to it directly.
     */
    Map<String, Set<String>> activeClaimsExcluding(String selfKey);

    /**
     * Load a static-shipped rule file into the engine's internal applied state. The engine
     * builds whatever lightweight Applied artifact its unregister path needs (metric-name
     * set for MAL, registered-rule list for LAL) and stores it under {@code "catalog:name"}
     * key. Returns {@code true} when an entry was loaded, {@code false} when no claims were
     * enumerable from {@code content} (empty rule file) or the engine already has an entry
     * for this key.
     *
     * <p>Called by {@code StaticRuleLoader} at boot and on tick-time orphan-recovery; lets
     * the loader stay DSL-agnostic — it doesn't need to know whether the engine's applied
     * state is keyed on metric names or {@code (layer, ruleName)} tuples.
     */
    boolean loadStaticRuleFile(String catalog, String name, String content);

    /**
     * Build the engine's concrete {@link ApplyContext} subtype from the shared
     * {@link ApplyInputs} the scheduler hands every call. The engine plugs in any
     * DSL-specific state it carries internally (e.g. the per-key applied artifact map).
     */
    C newApplyContext(ApplyInputs inputs);

    /**
     * Phase: compile. Produces a {@link CompiledDSL} that carries the engine's per-file
     * generated classes + per-file classloader + delta info. NO backend DDL fired here, NO
     * scheduler-cache mutation. Throws {@link RuntimeException} on compile failure;
     * scheduler stamps {@code applyError} on the snapshot and surfaces to the caller.
     */
    CompiledDSL compile(RuntimeRuleManagementDAO.RuntimeRuleFile file,
                           Classification classification,
                           C ctx);

    /**
     * Phase: schema changes. Drive the listener chain (BanyanDB define / drop, ES index
     * mapping, JDBC table, etc.) for the deltas this CompiledDSL represents. The
     * {@code StorageManipulationOpt} on the context controls whether the listeners actually
     * fire (full / localCacheOnly / localCacheVerify). LAL impl is a no-op (no backend
     * schema). Throws on backend failure; scheduler invokes {@link #rollback}.
     */
    void fireSchemaChanges(CompiledDSL compiled, C ctx);

    /**
     * Phase: verify. Post-DDL backend probe. Returns {@code null} on success, or an error
     * string the scheduler stamps on the snapshot's {@code applyError}. MAL: real
     * {@code isExists} round-trip per Model. LAL: no-op (returns {@code null}).
     */
    String verify(CompiledDSL compiled, C ctx);

    /**
     * Phase: commit. Swap the in-memory cache (engine-owned applied state + appliedContent
     * for this key), promote the freshly-built classloader via {@link
     * org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager#commit} and
     * retire any displaced prior loader through the manager, fire alarm-reset for the
     * affected metric name set via the context's alarmResetter callback. From this call
     * onward the bundle is live; up to this call all phases can be rolled back cleanly.
     */
    void commit(CompiledDSL compiled, C ctx);

    /**
     * Phase: rollback. Drop registrations from THIS attempt — the just-registered metrics
     * (MAL) or rule keys (LAL). Old applied state stays; scheduler hasn't swapped the
     * cache yet, so dispatch keeps serving the prior bundle. Idempotent.
     */
    void rollback(CompiledDSL compiled, C ctx);

    /**
     * Tear down a previously-applied bundle (or a static-only bundle). Driven by
     * {@code /inactivate} (with {@code localCacheOnly} so backend stays), {@code /delete}
     * (with {@code fullInstall} so backend drops), and the tick's gone-keys cleanup on main.
     * Engine clears its own dispatcher state + per-key applied entry. Shared post-cleanup
     * (content-cache clear) is the orchestrator's concern after this call returns.
     */
    void unregister(String catalog, String name, C ctx);

    /**
     * Discharge backend schema for {@code /delete}. By the time the REST handler invokes
     * {@code /delete}, {@code /inactivate} has already cleared the engine's applied state
     * — a naive {@link #unregister} call would no-op the destructive cascade and the
     * backend resource would orphan once the DAO row is deleted. Engines that own backend
     * schema (MAL) re-register prototypes locally then tear down under fullInstall so the
     * listener chain runs the destructive cascade on the existing resource. Engines without
     * backend (LAL) implement this as a no-op — the row deletion alone discharges the rule.
     *
     * <p>{@code bundledContent} controls the destructiveness:
     * <ul>
     *   <li>{@code null} — destructive: drop all backend resources the runtime row
     *       claimed. The rule is being permanently removed (no bundled twin on disk to
     *       fall back to).</li>
     *   <li>non-null — delta: drop only metrics that {@code runtimeContent} claims but
     *       {@code bundledContent} does not, plus metrics in both at different shape.
     *       Bundled-shared metrics at matching shape are preserved (no data loss for the
     *       measures bundled will reuse on its synchronous reload). Used when {@code
     *       /delete} reverts to a bundled twin.</li>
     * </ul>
     *
     * <p>Throws {@link IllegalStateException} when a prerequisite fails (e.g., MeterSystem
     * unavailable, parse error in either content); the caller (the {@code DSLRuntimeDelete}
     * orchestrator) propagates the throw so the REST handler aborts the row deletion —
     * refusing to delete the row is the correct failure mode (an orphaned backend resource
     * with no DAO row to drive a retry is worse).
     */
    void dropBackend(String catalog, String name, String runtimeContent,
                     String bundledContent, C ctx);

    /**
     * After a runtime override has been removed for {@code (catalog, name)}, reload the
     * bundled rule from {@link
     * org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry} (if any) and bring
     * it back into service via a fresh {@code static:} loader from
     * {@link org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager}.
     *
     * <p>Returns {@code true} when a bundled rule was found and reinstalled; {@code false}
     * when no bundled rule exists for this key (the rule is genuinely gone) or the engine
     * doesn't participate in static fall-over (e.g. its catalog has no {@code StaticRuleRegistry}
     * entries).
     *
     * <p>Errors during reload propagate as {@link RuntimeException}s the orchestrator logs
     * but does not surface to the operator; the next dslManager tick will retry through the
     * normal classify/apply path against whatever DB state then exists.
     *
     * @param alarmResetter alarm-window reset callback for affected metric names. The
     *                      orchestrator picks the same callback it used for {@link
     *                      #unregister} so an "update path" tear-down (where the caller
     *                      drives reset itself) doesn't double-reset.
     * @param moduleManager scheduler-supplied module manager so the engine can resolve its
     *                      backend dispatcher (MeterSystem / LogFilterListener.Factory).
     */
    boolean reloadStatic(String catalog, String name, Consumer<Set<String>> alarmResetter,
                         ModuleManager moduleManager);
}
