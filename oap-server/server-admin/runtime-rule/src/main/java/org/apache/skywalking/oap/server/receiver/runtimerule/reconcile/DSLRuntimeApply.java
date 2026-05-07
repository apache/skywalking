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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyContext;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.CompiledDSL;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.EngineCompileException;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;

/**
 * DSL-agnostic apply orchestrator. Symmetric counterpart to {@link DSLRuntimeUnregister}: where
 * {@code DSLRuntimeUnregister} runs the engine's tear-down phase, {@code DSLRuntimeApply} runs
 * the engine's compile-through-commit phase pipeline. The scheduler calls into this class for
 * every classify result that warrants applying (NEW / FILTER_ONLY / STRUCTURAL); INACTIVE
 * routes to {@link DSLRuntimeUnregister}; NO_CHANGE never reaches here.
 *
 * <p>What this class owns:
 * <ul>
 *   <li>Engine lookup via {@link RuleEngineRegistry} and the per-engine context construction
 *       ({@link RuleEngine#newApplyContext}).</li>
 *   <li>Phase pipeline: {@link RuleEngine#compile} → {@link RuleEngine#fireSchemaChanges} →
 *       {@link RuleEngine#verify} → {@link RuleEngine#rollback} (on verify failure) or
 *       {@link RuleEngine#commit}.</li>
 *   <li>Reporting outcomes via {@link Outcome} so the scheduler can drive snapshot transitions
 *       + persistence + suspend coordination uniformly across engines.</li>
 * </ul>
 *
 * <p>What this class does NOT own — the scheduler keeps these because they cross engines
 * and depend on snapshot / cluster state:
 * <ul>
 *   <li>Cross-file ownership guard (queries DAO + appliedX across all engines).</li>
 *   <li>Snapshot transitions ({@link
 *       org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState} mutations).</li>
 *   <li>Suspend / Resume coordinator interactions.</li>
 *   <li>STRUCTURAL deferred-commit stash via {@link StructuralCommitCoordinator}.</li>
 * </ul>
 *
 * <p><b>Deferred commit.</b> The scheduler can invoke {@link #compileAndVerify} (no commit)
 * for the STRUCTURAL REST 2-PC path, then drive {@link #commit} or {@link #rollback}
 * separately after row-persist resolves. The simpler {@link #apply} variant does
 * compile + verify + commit in one call for the tick path and FILTER_ONLY REST path.
 *
 * <p><b>Loader kind.</b> All entry points take a {@link DSLClassLoaderManager.Kind} that
 * tags the per-file classloader. {@link DSLClassLoaderManager.Kind#RUNTIME} for {@code
 * /addOrUpdate} and tick paths; {@link DSLClassLoaderManager.Kind#BUNDLED} for the
 * {@code /delete?mode=revertToBundled} path that re-installs the bundled YAML through
 * this same pipeline.
 */
@Slf4j
public final class DSLRuntimeApply {

    private final RuleEngineRegistry engineRegistry;

    public DSLRuntimeApply(final RuleEngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    /**
     * Run compile → fireSchemaChanges → verify → commit (or rollback on verify failure) all in
     * one call. Used by the tick path and the FILTER_ONLY REST path where there is no row-
     * persist gate to wait on.
     */
    public Outcome apply(final RuntimeRuleManagementDAO.RuntimeRuleFile file,
                         final Classification classification,
                         final DSLClassLoaderManager.Kind kind,
                         final ApplyInputs inputs) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(file.getCatalog());
        if (engine == null) {
            return Outcome.compileFailed(
                "no engine registered for catalog '" + file.getCatalog() + "'", null);
        }
        return applyTyped(engine, file, classification, kind, inputs);
    }

    /**
     * Run compile → fireSchemaChanges → verify only. Returns an outcome the caller can hold;
     * the caller drives {@link #commit} after its own external precondition resolves (row-
     * persist for the REST STRUCTURAL path) or {@link #rollback} on the precondition failing.
     */
    public Outcome compileAndVerify(final RuntimeRuleManagementDAO.RuntimeRuleFile file,
                                    final Classification classification,
                                    final DSLClassLoaderManager.Kind kind,
                                    final ApplyInputs inputs) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(file.getCatalog());
        if (engine == null) {
            return Outcome.compileFailed(
                "no engine registered for catalog '" + file.getCatalog() + "'", null);
        }
        return compileAndVerifyTyped(engine, file, classification, kind, inputs);
    }

    /** Drive {@code engine.commit} on a previously {@link #compileAndVerify}-produced outcome. */
    public void commit(final Outcome outcome) {
        if (outcome.compiled == null || outcome.engine == null || outcome.ctx == null) {
            throw new IllegalStateException(
                "DSLRuntimeApply.commit called on an outcome without compiled state: " + outcome.status);
        }
        commitTyped(outcome);
    }

    /** Drive {@code engine.rollback} on a previously {@link #compileAndVerify}-produced
     *  outcome. Used when the orchestrator's row-persist (or any other post-verify external
     *  precondition) fails. */
    public void rollback(final Outcome outcome) {
        if (outcome.compiled == null || outcome.engine == null || outcome.ctx == null) {
            return; // nothing to roll back
        }
        rollbackTyped(outcome);
    }

    private static <C extends ApplyContext> Outcome applyTyped(
            final RuleEngine<C> engine,
            final RuntimeRuleManagementDAO.RuntimeRuleFile file,
            final Classification classification,
            final DSLClassLoaderManager.Kind kind,
            final ApplyInputs inputs) {
        final Outcome step = compileAndVerifyTypedHelper(engine, file, classification, kind, inputs);
        if (step.status != Outcome.Status.READY_TO_COMMIT) {
            return step;
        }
        @SuppressWarnings("unchecked")
        final C ctx = (C) step.ctx;
        engine.commit(step.compiled, ctx);
        return Outcome.committed(engine, step.compiled, ctx);
    }

    private static <C extends ApplyContext> Outcome compileAndVerifyTyped(
            final RuleEngine<C> engine,
            final RuntimeRuleManagementDAO.RuntimeRuleFile file,
            final Classification classification,
            final DSLClassLoaderManager.Kind kind,
            final ApplyInputs inputs) {
        return compileAndVerifyTypedHelper(engine, file, classification, kind, inputs);
    }

    private static <C extends ApplyContext> Outcome compileAndVerifyTypedHelper(
            final RuleEngine<C> engine,
            final RuntimeRuleManagementDAO.RuntimeRuleFile file,
            final Classification classification,
            final DSLClassLoaderManager.Kind kind,
            final ApplyInputs inputs) {
        final C ctx = engine.newApplyContext(inputs);
        final CompiledDSL compiled;
        try {
            compiled = engine.compile(file, classification, kind, ctx);
        } catch (final EngineCompileException ece) {
            log.error("runtime-rule apply: compile FAILED for {}/{}: {}",
                file.getCatalog(), file.getName(), ece.getMessage(), ece);
            return Outcome.compileFailed(ece.getMessage(), null);
        } catch (final RuntimeException re) {
            log.error("runtime-rule apply: compile threw unexpectedly for {}/{}: {}",
                file.getCatalog(), file.getName(), re.getMessage(), re);
            return Outcome.compileFailed(re.getMessage(), null);
        }
        // fireSchemaChanges is a no-op for both engines today; left as an SPI hook for future
        // engines whose listener chain isn't fused with compile.
        engine.fireSchemaChanges(compiled, ctx);
        final String verifyError = engine.verify(compiled, ctx);
        if (verifyError != null) {
            engine.rollback(compiled, ctx);
            return Outcome.verifyFailed(verifyError, engine, compiled, ctx);
        }
        return Outcome.readyToCommit(engine, compiled, ctx);
    }

    @SuppressWarnings("unchecked")
    private static <C extends ApplyContext> void commitTyped(final Outcome outcome) {
        final RuleEngine<C> engine = (RuleEngine<C>) outcome.engine;
        engine.commit(outcome.compiled, (C) outcome.ctx);
    }

    @SuppressWarnings("unchecked")
    private static <C extends ApplyContext> void rollbackTyped(final Outcome outcome) {
        final RuleEngine<C> engine = (RuleEngine<C>) outcome.engine;
        engine.rollback(outcome.compiled, (C) outcome.ctx);
    }

    /**
     * Result of an apply attempt. The scheduler reads {@link #status} to drive its own
     * snapshot transition + persistence:
     * <ul>
     *   <li>{@link Status#COMMITTED} — engine committed; scheduler advances snapshot to
     *       RUNNING with the new content hash.</li>
     *   <li>{@link Status#READY_TO_COMMIT} — engine compiled + verified, awaiting external
     *       precondition (row-persist) before {@link DSLRuntimeApply#commit} is invoked.</li>
     *   <li>{@link Status#COMPILE_FAILED} — engine threw on compile; engine has already rolled
     *       back its partial state. Scheduler stamps {@code applyError} on the snapshot
     *       without advancing the content hash so the next tick retries.</li>
     *   <li>{@link Status#VERIFY_FAILED} — compile succeeded, verify rejected; engine rollback
     *       has already run. Scheduler stamps {@code applyError}.</li>
     * </ul>
     */
    public static final class Outcome {
        public enum Status { COMMITTED, READY_TO_COMMIT, COMPILE_FAILED, VERIFY_FAILED }

        public final Status status;
        public final String error;
        public final CompiledDSL compiled;
        final RuleEngine<?> engine;
        final ApplyContext ctx;

        private Outcome(final Status status, final String error, final CompiledDSL compiled,
                        final RuleEngine<?> engine, final ApplyContext ctx) {
            this.status = status;
            this.error = error;
            this.compiled = compiled;
            this.engine = engine;
            this.ctx = ctx;
        }

        static Outcome committed(final RuleEngine<?> engine, final CompiledDSL compiled,
                                 final ApplyContext ctx) {
            return new Outcome(Status.COMMITTED, null, compiled, engine, ctx);
        }

        static Outcome readyToCommit(final RuleEngine<?> engine, final CompiledDSL compiled,
                                     final ApplyContext ctx) {
            return new Outcome(Status.READY_TO_COMMIT, null, compiled, engine, ctx);
        }

        static Outcome compileFailed(final String error, final CompiledDSL compiled) {
            return new Outcome(Status.COMPILE_FAILED, error, compiled, null, null);
        }

        static Outcome verifyFailed(final String error, final RuleEngine<?> engine,
                                    final CompiledDSL compiled, final ApplyContext ctx) {
            return new Outcome(Status.VERIFY_FAILED, error, compiled, engine, ctx);
        }
    }
}
