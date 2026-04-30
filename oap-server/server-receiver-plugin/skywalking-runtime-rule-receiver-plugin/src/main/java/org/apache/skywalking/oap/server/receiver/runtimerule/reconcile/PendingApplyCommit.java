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

import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;

/**
 * Pre-compiled-and-verified engine output the scheduler holds while the REST handler waits
 * on row-persist. The opaque {@link DSLRuntimeApply.Outcome} wraps the engine's
 * {@code CompiledDSL} + {@code ApplyContext} + the type-safe commit/rollback dispatch
 * helpers; we hold it as-is so {@link StructuralCommitCoordinator} can finalize via
 * {@code dslRuntimeApply.commit(outcome)} or discard via {@code dslRuntimeApply.rollback}
 * without re-implementing engine work.
 *
 * <p>Scheduler-side state ({@link #prevSnapshot}, {@link #wasSuspended}, {@link #commitNowMs})
 * is what drives the snapshot transition + suspend resume after the engine commits. These
 * don't belong on the engine's CompiledDSL because they're bookkeeping the scheduler owns.
 */
public final class PendingApplyCommit {

    final DSLRuntimeApply.Outcome outcome;
    final DSLRuntimeState prevSnapshot;
    final boolean wasSuspended;
    final long commitNowMs;

    public PendingApplyCommit(final DSLRuntimeApply.Outcome outcome,
                                   final DSLRuntimeState prevSnapshot,
                                   final boolean wasSuspended,
                                   final long commitNowMs) {
        this.outcome = outcome;
        this.prevSnapshot = prevSnapshot;
        this.wasSuspended = wasSuspended;
        this.commitNowMs = commitNowMs;
    }

    public String catalog() {
        return outcome.compiled.getCatalog();
    }

    public String name() {
        return outcome.compiled.getName();
    }

    public String newContentHash() {
        return outcome.compiled.getContentHash();
    }
}
