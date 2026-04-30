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

package org.apache.skywalking.oap.server.receiver.runtimerule.state;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;

/**
 * One DSL rule script as the dslManager currently holds it on this node — every per-file
 * piece of state in one immutable record. Updates produce a new instance via the
 * {@code with*} builders, so a {@link java.util.concurrent.ConcurrentMap#compute compute} on
 * the dslManager's {@code rules} map gives atomic per-key transitions without an external
 * lock.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #catalog}, {@link #name} — identity.</li>
 *   <li>{@link #content} — the YAML last successfully applied. The engine's {@code classify}
 *       reads this as the "old side" of the next delta; {@code /list} surfaces it (or its
 *       hash) to the operator. {@code null} until the first successful commit, cleared back
 *       to {@code null} on unregister.</li>
 *   <li>{@link #state} — operator-facing per-key view ({@link DSLRuntimeState}: RUNNING /
 *       SUSPENDED / NOT_LOADED, {@code suspendOrigin}, {@code lastApplyError}, timestamps).
 *       Returned verbatim on {@code /list}.</li>
 *   <li>{@link #lock} — per-file outermost {@link ReentrantLock}. Identity is stable across
 *       {@code with*} builders so consecutive transitions on the same rule serialize on the
 *       same mutex. Cluster Suspend RPCs, REST workflows, the dslManager tick, and inline
 *       sync apply paths all acquire this lock by going through
 *       {@code rules.computeIfAbsent(key, k -> empty(catalog, name)).getLock()} — the entry
 *       is lazy-created on first lock so callers never have to ask "does the rule exist
 *       yet?" before locking.</li>
 *   <li>{@link #applied} — engine-opaque artefact the engine wrote on its last successful
 *       commit. {@code null} until the first commit, cleared on unregister. The
 *       {@link EngineApplied} interface lets cross-DSL code (Suspend/Resume coordinator,
 *       cross-file ownership guard, classloader graveyard hand-off) drive dispatch and
 *       claim queries polymorphically without switching on MAL vs LAL; engines cast to
 *       their richer subtype when they need the full Applied.</li>
 * </ul>
 *
 * <p>This class consolidates what used to be four parallel per-key maps on the dslManager
 * (snapshot {@code DSLRuntimeState}, {@code appliedContent} YAML, {@code PerFileLockMap}
 * locks, {@code appliedMal}/{@code appliedLal} engine artefacts). Per-rule operations —
 * classify, apply, unregister, suspend, resume, persist, /list — read or replace one
 * {@code AppliedRuleScript} instead of coordinating across maps.
 */
@Getter
public final class AppliedRuleScript {

    private final String catalog;
    private final String name;
    private final String content;
    private final DSLRuntimeState state;
    private final ReentrantLock lock;
    private final EngineApplied applied;

    /**
     * Construct a fresh entry with a brand-new {@link ReentrantLock} and no applied artefact.
     * Used on the first time a {@code (catalog, name)} pair is seen — either via lazy lock
     * acquire on the rules map or an explicit static-rule load.
     */
    public AppliedRuleScript(final String catalog, final String name, final String content,
                             final DSLRuntimeState state) {
        this(catalog, name, content, state, new ReentrantLock(), null);
    }

    /**
     * Internal constructor used by the {@code with*} builders to preserve the lock identity
     * + applied artefact. Public so engines / tests can construct a freshly-applied script
     * with a specific {@link EngineApplied} when needed.
     */
    public AppliedRuleScript(final String catalog, final String name, final String content,
                             final DSLRuntimeState state, final ReentrantLock lock,
                             final EngineApplied applied) {
        this.catalog = catalog;
        this.name = name;
        this.content = content;
        this.state = state;
        this.lock = lock;
        this.applied = applied;
    }

    /**
     * Build a fresh entry for {@code (catalog, name)} with no content, no state, no applied
     * artefact, and a fresh {@link ReentrantLock}. Used by the rules map's
     * {@code computeIfAbsent} so callers can lock for a {@code (catalog, name)} before the
     * rule has any state of its own.
     */
    public static AppliedRuleScript empty(final String catalog, final String name) {
        return new AppliedRuleScript(catalog, name, null, null);
    }

    public AppliedRuleScript withContent(final String newContent) {
        return new AppliedRuleScript(catalog, name, newContent, state, lock, applied);
    }

    public AppliedRuleScript withState(final DSLRuntimeState newState) {
        return new AppliedRuleScript(catalog, name, content, newState, lock, applied);
    }

    public AppliedRuleScript withApplied(final EngineApplied newApplied) {
        return new AppliedRuleScript(catalog, name, content, state, lock, newApplied);
    }

    public AppliedRuleScript withContentAndState(final String newContent,
                                                 final DSLRuntimeState newState) {
        return new AppliedRuleScript(catalog, name, newContent, newState, lock, applied);
    }

    public AppliedRuleScript withContentAndApplied(final String newContent,
                                                   final EngineApplied newApplied) {
        return new AppliedRuleScript(catalog, name, newContent, state, lock, newApplied);
    }

    /**
     * Lazy-acquire the per-file {@link ReentrantLock} for {@code (catalog, name)} on
     * {@code rules}. Used by every caller that needs to serialise on a {@code (catalog, name)}
     * before the rule has any state of its own — the entry is auto-created with
     * {@link #empty} on first call and the lock returned has stable identity across
     * subsequent {@code with*} replacements of the entry.
     *
     * <p>Centralised here so the lazy-create pattern lives in one place instead of being
     * duplicated at every dependent's call site.
     */
    public static ReentrantLock lockFor(final Map<String, AppliedRuleScript> rules,
                                        final String catalog, final String name) {
        return rules.computeIfAbsent(catalog + ":" + name,
            k -> empty(catalog, name)).getLock();
    }
}

