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
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyContext;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * DSL-agnostic teardown orchestrator. The scheduler calls
 * {@link #unregister(String, String, boolean, StorageManipulationOpt)} from every shared-pipeline
 * step that removes registrations: the tick's INACTIVE branch and gone-keys cleanup, the apply
 * path's {@code isInactive} short-circuit, and the destructive {@code /delete} dropper.
 *
 * <p>Routing: the orchestrator looks up the {@link RuleEngine} for the file's catalog via
 * {@link RuleEngineRegistry}, asks it to build its own {@link ApplyContext} subtype from the
 * shared {@link ApplyInputs}, and dispatches the engine's {@code unregister}. The engine owns
 * everything DSL-specific (backend cascade, applied-entry removal, classloader retire,
 * static-rule fallback, alarm reset target). The orchestrator owns the cross-DSL bookkeeping
 * (clearing the content side of {@link AppliedRuleScript} on success).
 *
 * <p>After a successful teardown, the engine's {@code reloadStatic} hook is invoked so any
 * bundled-static rule that the now-removed runtime override was masking gets brought back into
 * service via a fresh {@code static:} loader from
 * {@link org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager}.
 *
 * <p><b>{@code invokeAlarmOnRemove}.</b> Two legitimate call modes:
 * <ul>
 *   <li>Full tear-down ({@code status→INACTIVE}, {@code /delete}, gone-keys cleanup): pass
 *       {@code true}. The engine's prior-bundle metric set is the authoritative reset target —
 *       no new bundle is coming.</li>
 *   <li>Update path (the caller is about to re-register): pass {@code false}. The orchestrator
 *       hands the engine a no-op alarm resetter so the engine's existing reset call is
 *       neutralised; the caller drives the reset itself using the classifier's precise delta.</li>
 * </ul>
 */
@Slf4j
public final class DSLRuntimeUnregister {

    private static final Consumer<Set<String>> NO_OP_ALARM_RESETTER = s -> {
    };

    private final Map<String, AppliedRuleScript> rules;
    private final ModuleManager moduleManager;
    private final Consumer<Set<String>> alarmResetter;
    private final RuleEngineRegistry engineRegistry;

    public DSLRuntimeUnregister(final Map<String, AppliedRuleScript> rules,
                                final ModuleManager moduleManager,
                                final Consumer<Set<String>> alarmResetter,
                                final RuleEngineRegistry engineRegistry) {
        this.rules = rules;
        this.moduleManager = moduleManager;
        this.alarmResetter = alarmResetter;
        this.engineRegistry = engineRegistry;
    }

    public boolean unregister(final String catalog, final String name,
                              final boolean invokeAlarmOnRemove,
                              final StorageManipulationOpt storageOpt) {
        return unregister(catalog, name, invokeAlarmOnRemove, storageOpt, false);
    }

    /**
     * Tear down a bundle's local registrations. {@code reloadStaticAfter} controls whether
     * the bundled rule (if any) is reinstalled after the unregister:
     *
     * <ul>
     *   <li>{@code false} — used by {@code /inactivate} and the apply path's INACTIVE
     *       classification. The operator deliberately turned the rule OFF; bringing the
     *       bundled twin back instantly would defeat the soft-pause contract. The local
     *       state is left at {@code NOT_LOADED}.</li>
     *   <li>{@code true} — used by the row-gone reconcile path (a {@code /delete} cleared
     *       the row, peer ticks observe the absence). The runtime override no longer
     *       exists, so the bundled YAML (if any) should serve again — engines reload via
     *       {@link RuleEngine#reloadStatic} into a fresh {@code static:} loader.</li>
     * </ul>
     *
     * @return {@code true} when a bundled fall-over was actually installed (caller may want
     *         to retain the entry in the unified rules map rather than removing it);
     *         {@code false} otherwise (no engine, no bundled twin, reload failed, or
     *         {@code reloadStaticAfter=false}).
     */
    public boolean unregister(final String catalog, final String name,
                              final boolean invokeAlarmOnRemove,
                              final StorageManipulationOpt storageOpt,
                              final boolean reloadStaticAfter) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(catalog);
        if (engine == null) {
            log.warn("runtime-rule dslManager: no engine registered for catalog '{}' on "
                + "unregister of {}/{}; skipping", catalog, catalog, name);
            return false;
        }
        final Consumer<Set<String>> resetter =
            invokeAlarmOnRemove ? alarmResetter : NO_OP_ALARM_RESETTER;
        final ApplyInputs inputs = new ApplyInputs(moduleManager, storageOpt, resetter, rules);
        runEngineUnregister(engine, catalog, name, inputs);

        // Cross-DSL bookkeeping: clear the cached raw content so the next classify call sees
        // "no prior bundle". Engines deliberately don't touch this — it's shared between
        // catalogs and the orchestrator owns the lifecycle. State is preserved (set
        // elsewhere — INACTIVE tombstone, NOT_LOADED, or reset by reloadStatic below).
        rules.computeIfPresent(DSLScriptKey.key(catalog, name),
            (k, prev) -> prev.withContent(null));

        if (!reloadStaticAfter) {
            return false;
        }
        try {
            return engine.reloadStatic(catalog, name, resetter, moduleManager);
        } catch (final Throwable t) {
            log.warn("runtime-rule dslManager: static fall-over reload failed for {}/{}; "
                + "bundled rule may stay dark until a successful re-apply or restart",
                catalog, name, t);
            return false;
        }
    }

    /** Wildcard-capture helper that lets {@code engine.unregister} be called against a
     *  {@code RuleEngine<?>} without an unchecked cast. */
    private static <C extends ApplyContext> void runEngineUnregister(
        final RuleEngine<C> engine, final String catalog, final String name,
        final ApplyInputs inputs) {
        final C ctx = engine.newApplyContext(inputs);
        engine.unregister(catalog, name, ctx);
    }
}
