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

package org.apache.skywalking.oap.server.core.dsldebug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

/**
 * Per-rule capture binding consulted by every probe call site that the DSL
 * generators emit. One instance lives on each compiled rule's runtime
 * artifact (MAL / LAL singletons, OAL dispatcher) — the holder is the only
 * piece of state the probe touches on the receiver hot path.
 *
 * <h2>Hot-path read</h2>
 * Probes call {@link #isGateOn()} once and short-circuit when {@code false}
 * (idle path, JIT inlines and eliminates the branch after warm-up). When
 * {@code true}, probes call {@link #getRecorders()} and iterate the snapshot,
 * fanning out to every bound {@link DebugRecorder}. After JIT warm-up these
 * are byte-identical to direct volatile-field loads — encapsulation costs
 * nothing once the inliner runs.
 *
 * <h2>Mutation</h2>
 * Session install / uninstall calls
 * {@link #addRecorder(DebugRecorder)} / {@link #removeRecorder(DebugRecorder)}
 * directly. Both are {@code synchronized} to serialise the copy-on-write swap
 * of the recorder array — a handful of events per session, never on the hot
 * path. The gate flips automatically on the {@code 0 ↔ 1} recorder
 * transitions, so callers never have to remember to flip it.
 *
 * <h2>Hot-update interaction</h2>
 * {@code content} is the verbatim rule source captured at construction
 * time. A hot-update of the rule allocates a brand-new holder on the new
 * compiled class with the new source text. The old holder continues to
 * exist (referenced by ingest threads still running the old class) until
 * its drain completes; pre-update recorders carry the old text;
 * post-update sessions bind to the new holder. The UI gets the rule
 * source inline on every record — no second round-trip to a "fetch DSL by
 * hash" endpoint, no risk of looking at the wrong revision.
 */
public final class GateHolder {

    private static final DebugRecorder[] EMPTY = new DebugRecorder[0];

    /**
     * Hot-path read by every probe. Volatile so a session install is visible
     * to receiver threads on the next probe call without a memory barrier on
     * either side.
     */
    private volatile boolean gateOn;

    /**
     * Copy-on-write snapshot of currently-bound recorders. Volatile so the
     * publish on install / uninstall is visible immediately. Readers MUST
     * treat the array as immutable — writers replace the reference, never
     * mutate the array in place.
     */
    private volatile DebugRecorder[] recorders = EMPTY;

    /**
     * Verbatim rule source captured at construction time — the LAL DSL
     * block, the OAL .oal file content, or the MAL rule's exp body.
     * Threaded onto every captured record so the UI can render the source
     * inline. {@code ""} when the wiring path didn't supply text (mock
     * holders in tests, MAL static-loader paths).
     */
    @Getter
    private final String content;

    /**
     * Catalog-specific structured metadata — populated by the per-DSL
     * compile path after construction (the codegen emits
     * {@code new GateHolder("...")} with content only; the caller fills
     * structured fields via {@link #setMetadata}). The recorder reads
     * this once at construction time and stamps it onto every captured
     * {@code ExecutionRecord} for UI rendering. Kept as a
     * {@code @code} reference rather than {@code @link} since
     * {@code ExecutionRecord} lives in the {@code dsl-debugging} module
     * (downstream of {@code server-core}).
     *
     * <p>Conventional keys per catalog:
     * <ul>
     *   <li>MAL — {@code metricPrefix}, {@code name}, {@code filter},
     *       {@code exp}, {@code expSuffix}.</li>
     *   <li>LAL — {@code outputClass} (the FQCN/simpleName of the typed
     *       output the rule produces).</li>
     *   <li>OAL — currently empty (the verbatim {@code content} is the
     *       single-line OAL declaration which is already structured).</li>
     * </ul>
     *
     * <p>Volatile so a setMetadata call from the compile path is visible
     * to receiver threads on the next probe call without a memory barrier
     * on either side. Set once shortly after construction and not
     * mutated again — but volatile is cheap and removes any risk of a
     * surprise read returning the empty default after publication.
     */
    private volatile Map<String, String> metadata = Collections.emptyMap();

    public GateHolder(final String content) {
        this.content = content == null ? "" : content;
    }

    /**
     * Convenience factory for codegen-emitted holders that need to
     * stamp structured rule metadata at instance-init time. Equivalent
     * to {@code new GateHolder(content)} followed by {@code setMetadata}
     * — but expressed as a single static call so Javassist field
     * initializers can use it without juggling a second statement.
     *
     * <p>Used by the OAL dispatcher codegen (one holder per metric) to
     * thread {@code {ruleName, sourceLine}} onto the holder so the
     * dsl-debugging records carry a structured per-rule envelope
     * alongside the verbatim {@code dsl}.
     */
    public static GateHolder withMetadata(final String content,
                                          final String ruleName,
                                          final int sourceLine) {
        final GateHolder holder = new GateHolder(content);
        final LinkedHashMap<String, String> meta =
            new LinkedHashMap<>();
        if (ruleName != null && !ruleName.isEmpty()) {
            meta.put("ruleName", ruleName);
        }
        if (sourceLine > 0) {
            meta.put("sourceLine", Integer.toString(sourceLine));
        }
        holder.setMetadata(meta);
        return holder;
    }

    /**
     * Snapshot of the structured metadata. Returns an immutable view —
     * recorders and the REST handler may iterate freely without locking.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Replace the structured metadata. Called once by the per-DSL
     * compile path after the rule's MetricRuleConfig (or equivalent)
     * fields are known. The recorder snapshots this at session install;
     * later mutations don't affect already-captured records.
     */
    public void setMetadata(final Map<String, String> newMetadata) {
        if (newMetadata == null || newMetadata.isEmpty()) {
            metadata = Collections.emptyMap();
            return;
        }
        // Defensive copy + immutable view so concurrent readers can't
        // observe a mutation if the caller still holds a reference.
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(newMetadata));
    }

    /**
     * @return {@code true} while at least one recorder is bound. Inlined to a
     *         single volatile-bool load after JIT warm-up.
     */
    public boolean isGateOn() {
        return gateOn;
    }

    /**
     * @return the current recorders snapshot. Iteration is safe without
     *         locking — the array is immutable; writers publish a new
     *         reference, never mutate this one.
     */
    public DebugRecorder[] getRecorders() {
        return recorders;
    }

    /**
     * Bind a recorder. The {@code 0 → 1} transition flips
     * {@link #isGateOn()} to {@code true}.
     */
    public synchronized void addRecorder(final DebugRecorder recorder) {
        Objects.requireNonNull(recorder, "recorder");
        final DebugRecorder[] old = recorders;
        final DebugRecorder[] copy = new DebugRecorder[old.length + 1];
        System.arraycopy(old, 0, copy, 0, old.length);
        copy[old.length] = recorder;
        recorders = copy;
        if (old.length == 0) {
            gateOn = true;
        }
    }

    /**
     * Remove a recorder. The {@code 1 → 0} transition flips
     * {@link #isGateOn()} to {@code false}. Idempotent when the recorder
     * isn't bound (retention-timeout-vs-explicit-stop race).
     */
    public synchronized void removeRecorder(final DebugRecorder recorder) {
        Objects.requireNonNull(recorder, "recorder");
        final DebugRecorder[] old = recorders;
        int hit = -1;
        for (int i = 0; i < old.length; i++) {
            if (old[i] == recorder) {
                hit = i;
                break;
            }
        }
        if (hit < 0) {
            return;
        }
        if (old.length == 1) {
            recorders = EMPTY;
            gateOn = false;
            return;
        }
        final DebugRecorder[] copy = new DebugRecorder[old.length - 1];
        System.arraycopy(old, 0, copy, 0, hit);
        System.arraycopy(old, hit + 1, copy, hit, old.length - hit - 1);
        recorders = copy;
    }
}
