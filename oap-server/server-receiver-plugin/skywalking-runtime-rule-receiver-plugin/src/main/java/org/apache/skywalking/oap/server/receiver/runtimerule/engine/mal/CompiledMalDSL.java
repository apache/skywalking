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

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DSLDelta;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.MalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.CompiledDSL;

/**
 * MAL-specific {@link CompiledDSL} carrying the output of {@link MalRuleEngine#compile} all
 * the way through {@link MalRuleEngine#commit} (or rollback). Holds only what the engine
 * itself knows about the bundle — the orchestrator owns scheduler-side state (snapshot
 * transitions, persistence, suspend coordination) and reads CompiledMalDSL purely as the
 * engine's compile output.
 *
 * <p>Filter-only: {@code delta} is {@code null}, {@code addedPlusShapeBreak} is empty —
 * the path skips alarm reset and classloader retire intentionally (see
 * {@link MalRuleEngine#commit}).
 */
@Getter
@RequiredArgsConstructor
public final class CompiledMalDSL implements CompiledDSL {
    private final String catalog;
    private final String name;
    private final String contentHash;
    private final Classification classification;
    /** Raw YAML the bundle was compiled from, written into {@code appliedContent[key]} on
     *  commit so the next classify call has the prior content to diff against. */
    private final String content;
    /** Prior bundle for this key, or {@code null} on first apply. Held for classloader retire
     *  on commit. */
    private final MalFileApplier.Applied oldApplied;
    /** Freshly-compiled bundle. Live in MeterSystem from the moment compile returned —
     *  rollback uses {@link #addedPlusShapeBreak} to know what to undo. */
    private final MalFileApplier.Applied newApplied;
    /** Classifier verdict + delta sets ({@code added}, {@code removed}, {@code shapeBreak},
     *  {@code alarmResetSet}). {@code null} on FILTER_ONLY (compile path doesn't compute
     *  per-metric deltas there). */
    private final DSLDelta delta;
    /** Pre-merged {@code added ∪ shapeBreak} — the canonical rollback / verify target set. */
    private final Set<String> addedPlusShapeBreak;
}
