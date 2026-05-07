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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyContext;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * {@code /delete?mode=revertToBundled} orchestrator. Third orchestrator alongside
 * {@link DSLRuntimeApply} (NEW / FILTER_ONLY / STRUCTURAL apply) and
 * {@link DSLRuntimeUnregister} (INACTIVE / gone-keys tear-down).
 *
 * <p><b>Two paths through {@code /delete}.</b> The REST handler chooses based on operator
 * intent and bundled-twin presence:
 * <ul>
 *   <li>DEFAULT mode, no bundled twin — REST does {@code dao.delete} directly. The runtime
 *       was already torn down locally by the prior {@code /inactivate}; the backend measure
 *       (if any) stays as an inert artefact, matching bundled-rule deletion semantics.
 *       This orchestrator is not involved.</li>
 *   <li>DEFAULT mode, bundled twin exists — REST refuses with 409
 *       {@code requires_revert_to_bundled}. Operator must opt in.</li>
 *   <li>{@code revertToBundled} mode, bundled twin exists — REST calls
 *       {@link #revertToBundled} and then {@code dao.delete}. This is the schema-change
 *       path; bundled may have a different shape than runtime, so the runtime backend
 *       must be dropped cleanly before bundled installs its own measure.</li>
 *   <li>{@code revertToBundled} mode, no bundled twin — REST returns 400; this orchestrator
 *       is not invoked.</li>
 * </ul>
 *
 * <p><b>How the schema change happens.</b> {@code /inactivate} cleared the engine's applied
 * state, so a naive bundled apply has no prior state to diff against — it would just
 * register bundled's metrics and leave any runtime-only metrics orphaned. To get the
 * proper diff, {@link #revertToBundled} runs the steps below in order:
 * <pre>
 *   1. {@link RuleEngine#installRuntime} re-registers prior runtime claims under
 *      {@code withoutSchemaChange} (no backend touch). Now the rules map points at
 *      runtime's claim set as if it were ACTIVE again.
 *   2. {@link DSLRuntimeApply#apply} runs the standard pipeline against the bundled YAML
 *      with {@code Kind.BUNDLED} + {@code withSchemaChange}. Engine.compile sees the
 *      step-1 runtime install as prior, classifies STRUCTURAL, computes the runtime→bundled
 *      delta. Engine.commit fires {@code applier.remove(removedMetrics, withSchemaChange)}
 *      which drops runtime-only measures via the listener chain, and the new compile
 *      registers bundled-only measures. Bundled-shared metrics at matching shape are
 *      reused; at differing shape, the listener cascade reshapes them via the standard
 *      {@code allowStorageChange} contract.
 *   3. The rules-map entry's state is reset to {@code null} so the next gone-keys
 *      reconcile (peer ticks see the absent DAO row) treats this as boot-seeded bundled
 *      rather than a dangling ACTIVE.
 * </pre>
 *
 * <p>The REST handler invokes {@code dao.delete} after this orchestrator returns success;
 * a DAO failure leaves the local node with bundled installed and the runtime row still
 * present, which the next reconciler tick reapplies as runtime — eventually consistent,
 * and the operator can retry the revert.
 *
 * <p>The orchestrator re-acquires the per-file lock (REST already holds it; the lock is
 * reentrant) so the implementation is correct whether called inline or from a background
 * path.
 */
@Slf4j
public class DSLRuntimeDelete {

    private final RuleEngineRegistry engineRegistry;
    private final ModuleManager moduleManager;
    private final Map<String, AppliedRuleScript> rules;
    private final Consumer<Set<String>> alarmResetter;
    private final DSLRuntimeApply dslRuntimeApply;

    public DSLRuntimeDelete(final RuleEngineRegistry engineRegistry,
                            final ModuleManager moduleManager,
                            final Map<String, AppliedRuleScript> rules,
                            final Consumer<Set<String>> alarmResetter,
                            final DSLRuntimeApply dslRuntimeApply) {
        this.engineRegistry = engineRegistry;
        this.moduleManager = moduleManager;
        this.rules = rules;
        this.alarmResetter = alarmResetter;
        this.dslRuntimeApply = dslRuntimeApply;
    }

    /** Outcome of a {@link #revertToBundled} call. */
    public static final class Result {
        public enum Status {
            /** Steps 1–3 all succeeded. Bundled is now serving locally. */
            REVERTED,
            /** Step 1 succeeded; step 2 (bundled apply pipeline) failed. In practice this
             *  is almost always a backend-storage failure during DDL or verify — BanyanDB
             *  rejected the measure shape, the schema-barrier didn't propagate within the
             *  timeout, or the storage backend was unreachable. Bundled YAML parse and
             *  Javassist generation are theoretical failure modes but extremely rare
             *  (bundled YAML has already been loaded successfully at boot). The engine
             *  has self-rolled-back its partial registrations and the orchestrator has
             *  unregistered the step-1 runtime install, so local state matches the
             *  persisted INACTIVE row. The operator can retry the revert once the storage
             *  backend recovers. */
            BUNDLED_APPLY_FAILED,
            /** Cross-file ownership guard rejected the revert. Bundled's claims overlap
             *  with another active bundle. */
            REFUSED_CONFLICT,
            /** Pre-step bookkeeping failed (no engine for catalog, MeterSystem unavailable,
             *  bundled YAML missing, etc.). Local state has not been mutated. */
            PRECONDITION_FAILED
        }

        public final Status status;
        public final String error;

        Result(final Status status, final String error) {
            this.status = status;
            this.error = error;
        }
    }

    /**
     * Run the revert-to-bundled pipeline for {@code (catalog, name)}. The REST handler
     * has already verified that the bundled YAML twin exists on disk. Returns a
     * {@link Result} describing the outcome; the REST handler maps that to an HTTP
     * response and decides whether to proceed with {@code dao.delete}.
     */
    public Result revertToBundled(final String catalog, final String name,
                                  final String runtimeContent) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(catalog);
        if (engine == null) {
            return new Result(Result.Status.PRECONDITION_FAILED,
                "no engine registered for catalog '" + catalog + "'");
        }
        final Optional<String> bundled = StaticRuleRegistry.active().find(catalog, name);
        if (!bundled.isPresent()) {
            return new Result(Result.Status.PRECONDITION_FAILED,
                "no bundled YAML on disk for " + catalog + "/" + name);
        }
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
        perFile.lock();
        try {
            // Defence-in-depth ownership guard. After the revert, bundled's claims become
            // the live claim set for this key. Refuse if any of bundled's claims are
            // already owned by another active bundle — letting bundled register would
            // clobber whatever the other active bundle is currently serving.
            final List<String> activeConflicts = checkOwnershipConflicts(
                engine, catalog, name, bundled.get());
            if (!activeConflicts.isEmpty()) {
                return new Result(Result.Status.REFUSED_CONFLICT,
                    "/delete?mode=revertToBundled refused for " + catalog + "/" + name
                        + ": bundled claim(s) " + activeConflicts + " are owned by "
                        + "another active bundle. Update or /inactivate the conflicting "
                        + "bundle(s) first, or accept that the bundled rule is masked "
                        + "until they are released.");
            }
            return runRevert(engine, catalog, name, runtimeContent, bundled.get());
        } finally {
            perFile.unlock();
        }
    }

    private List<String> checkOwnershipConflicts(final RuleEngine<?> engine, final String catalog,
                                                 final String name, final String bundledContent) {
        final String selfKey = DSLScriptKey.key(catalog, name);
        final Set<String> planned = engine.claimedKeys(bundledContent, catalog + "/" + name);
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

    /** Wildcard-capture helper. Threads the engine's typed context through the three
     *  steps that need a strong-typed {@code C}: install runtime, run apply, reset state. */
    private <C extends ApplyContext> Result runRevert(final RuleEngine<C> engine,
                                                       final String catalog, final String name,
                                                       final String runtimeContent,
                                                       final String bundledContent) {
        // Step 1. Install runtime locally (no backend touch). The next step's compile
        // sees this as the prior state and computes the runtime→bundled delta against it.
        final ApplyInputs withoutSchema = new ApplyInputs(
            moduleManager, StorageManipulationOpt.withoutSchemaChange(),
            alarmResetter, rules);
        final C ctx = engine.newApplyContext(withoutSchema);
        try {
            engine.installRuntime(catalog, name, runtimeContent, ctx);
        } catch (final IllegalStateException ise) {
            return new Result(Result.Status.PRECONDITION_FAILED,
                "installRuntime failed for " + catalog + "/" + name + ": " + ise.getMessage());
        } catch (final Throwable t) {
            log.error("runtime-rule revertToBundled: installRuntime threw for {}/{}",
                catalog, name, t);
            return new Result(Result.Status.PRECONDITION_FAILED, t.getMessage());
        }

        // Step 2. Run the standard apply pipeline against bundled. Engine.commit drops
        // runtime-only measures via the delta, registers bundled-only measures, and
        // reuses bundled-shared measures at matching shape.
        final RuntimeRuleManagementDAO.RuntimeRuleFile bundledFile =
            new RuntimeRuleManagementDAO.RuntimeRuleFile(
                catalog, name, bundledContent, /* status */ null, /* updateTime */ 0L);
        final ApplyInputs withSchema = new ApplyInputs(
            moduleManager, StorageManipulationOpt.withSchemaChange(),
            alarmResetter, rules);
        final DSLRuntimeApply.Outcome outcome = dslRuntimeApply.apply(
            bundledFile, Classification.STRUCTURAL,
            DSLClassLoaderManager.Kind.BUNDLED, withSchema);
        if (outcome.status != DSLRuntimeApply.Outcome.Status.COMMITTED) {
            log.warn("runtime-rule revertToBundled: bundled apply did not commit for {}/{}: {} ({})",
                catalog, name, outcome.error, outcome.status);
            // Step 1 installed runtime locally under withoutSchemaChange — handlers and
            // meterPrototypes are live again. Bundled apply failed (engine self-rolled
            // back its own partial registrations) but step 1 is still in place. The
            // operator's intent was /inactivate (handlers OFF) followed by a failed
            // revert — leaving runtime live silently violates the inactivate. Tear it
            // back down so local state matches the persisted INACTIVE row. The operator
            // must retry /delete?mode=revertToBundled after fixing the bundled YAML.
            final ApplyInputs cleanup = new ApplyInputs(
                moduleManager, StorageManipulationOpt.withoutSchemaChange(),
                alarmResetter, rules);
            final C cleanupCtx = engine.newApplyContext(cleanup);
            engine.unregister(catalog, name, cleanupCtx);
            return new Result(Result.Status.BUNDLED_APPLY_FAILED, outcome.error);
        }

        // Step 3. Mark the entry boot-seeded so gone-keys reconcile leaves it alone after
        // dao.delete removes the row. Without this reset, the next tick would see state
        // != null + DAO row absent and tear down what we just installed.
        rules.computeIfPresent(DSLScriptKey.key(catalog, name),
            (k, prev) -> prev.withState(null));
        return new Result(Result.Status.REVERTED, null);
    }
}
