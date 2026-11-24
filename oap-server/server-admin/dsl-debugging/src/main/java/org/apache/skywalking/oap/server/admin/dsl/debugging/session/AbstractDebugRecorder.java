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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.dsl.debug.DebugRecorder;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;

/**
 * Common machinery every per-DSL recorder needs: identity, the bound
 * holder's content, the list of captured executions, and the recordCap
 * bookkeeping that flips {@link #isCaptured()} once the bound is hit.
 *
 * <h2>Per-execution buffering</h2>
 * <p>Each pipeline run (one source / one log / one SampleFamily walked
 * end-to-end) becomes one {@link ExecutionRecord}. A per-thread
 * accumulator collects the {@link Sample}s as the rule walks; the
 * terminal probe (emit / meterEmit / outputRecord / outputMetric)
 * publishes the record into {@link #records} and clears the
 * accumulator. The cap is per-record (= per-execution) count.
 *
 * <h2>Per-record DSL stamp</h2>
 * <p>The recorder captures its bound holder's {@link GateHolder#getContent}
 * at construction — the verbatim rule source — and stamps it onto every
 * {@link ExecutionRecord} at allocation time. Per-record (not envelope-
 * level) so a hot-update mid-session that rotates the rule onto a fresh
 * compiled class doesn't make the captured records ambiguous: each
 * record carries the source it actually walked through. Cluster slices
 * that reach a node mid-rotation may carry different revisions on
 * different peers — keeping dsl per-record makes that visible without a
 * separate "which version did this peer have" round-trip.
 */
public abstract class AbstractDebugRecorder implements DebugRecorder {

    @Getter
    private final String sessionId;
    @Getter
    private final RuleKey ruleKey;
    @Getter
    private final String content;
    /**
     * Snapshot of the bound holder's catalog-specific metadata at session
     * install time. Stamped onto every {@link ExecutionRecord} this
     * recorder produces — the REST handler renders the metadata as a
     * structured {@code rule} object next to the verbatim {@code dsl}.
     */
    private final Map<String, String> metadata;
    private final SessionLimits limits;

    /**
     * The {@link GateHolder} this recorder is bound to. Held so that on
     * the {@code captured} state transition we can self-detach via
     * {@link GateHolder#removeRecorder} — this lets the holder flip the
     * gate off when the last recorder caps out, returning probe call
     * sites to the volatile-load-only fast path. {@code null} only in
     * unit-test paths that construct a recorder without a real holder.
     */
    private final GateHolder boundHolder;

    private final List<ExecutionRecord> records = new ArrayList<>();
    private volatile boolean captured;

    /**
     * Per-thread in-flight {@link ExecutionRecord}. The first
     * {@link #addSample} on a thread allocates one; the terminal probe
     * publishes it via {@link #publishCurrentExecution()}.
     */
    private final ThreadLocal<ExecutionRecord> current = new ThreadLocal<>();

    protected AbstractDebugRecorder(final String sessionId,
                                    final RuleKey ruleKey,
                                    final GateHolder boundHolder,
                                    final SessionLimits limits) {
        this.sessionId = sessionId;
        this.ruleKey = ruleKey;
        this.boundHolder = boundHolder;
        this.content = boundHolder == null ? "" : boundHolder.getContent();
        // Snapshot at install time: subsequent setMetadata calls on the
        // same holder (none expected today, but cheap to insulate against)
        // don't mutate already-captured records.
        this.metadata = boundHolder == null
            ? Collections.emptyMap()
            : boundHolder.getMetadata();
        this.limits = limits;
    }

    @Override
    public final String sessionId() {
        return sessionId;
    }

    @Override
    public final RuleKey ruleKey() {
        return ruleKey;
    }

    @Override
    public final boolean matches(final RuleKey candidate) {
        return ruleKey.equals(candidate);
    }

    @Override
    public final boolean isCaptured() {
        return captured;
    }

    /**
     * Append one sample to the per-thread in-flight execution. Allocates
     * a fresh execution record on the first call; subsequent calls land
     * in the same record until the terminal probe fires.
     *
     * <p>When the recorder has already reached recordCap, this also
     * clears any ThreadLocal still holding an in-flight execution from
     * before the cap. Otherwise a long-lived receiver thread that fires
     * just one more probe after capture would keep that orphan record
     * pinned to the thread's TL slot for the lifetime of the thread.
     */
    protected final void addSample(final Sample sample) {
        if (captured) {
            current.remove();
            return;
        }
        if (sample == null) {
            return;
        }
        ExecutionRecord exec = current.get();
        if (exec == null) {
            // Stamp the recorder's content (verbatim DSL source as of the
            // bound GateHolder) plus the catalog-specific metadata snapshot
            // onto the record at construction. Per-record so a hot-update
            // mid-session keeps cluster slices unambiguous when peers carry
            // different revisions of the same rule.
            exec = new ExecutionRecord(System.currentTimeMillis(), content, metadata);
            current.set(exec);
        }
        exec.append(sample);
    }

    /**
     * Drop the per-thread in-flight execution without publishing. Called
     * from filter-rejection paths so noise samples never reach the response.
     *
     * <p><b>Why kept-only capture.</b>
     * MAL / OAL pipelines route every routed-by-name SampleFamily / source
     * to the rule, then the DSL's filter clause (tag-level for MAL, column
     * predicate for OAL) decides whether the rule actually processes it.
     * On tag-discriminating rules the rejection ratio can hit 99% — most
     * traffic isn't relevant to the rule. If we published rejected
     * executions, recordCap (default 100) would fill with garbage before
     * any meaningful "what did the rule actually do" record showed up. So we
     * publish only kept executions: every record in {@code records[]}
     * represents one source/family that survived the rule's filter and
     * walked through to the terminal emit.
     */
    protected final void discardCurrentExecution() {
        current.remove();
    }

    /**
     * Publish the per-thread in-flight execution into the records list
     * and clear the accumulator. Called from the recorder's terminal
     * probe (emit / meterEmit / outputRecord / outputMetric). Empty
     * executions are dropped so a probe that fires only the terminal
     * stage doesn't pollute records[] with no-content rows.
     */
    protected final void publishCurrentExecution() {
        final ExecutionRecord exec = current.get();
        if (exec == null) {
            return;
        }
        current.remove();
        publishRecord(exec);
    }

    /**
     * @return the bound rule's catalog-specific metadata, as it was when the session installed
     */
    protected final Map<String, String> ruleMetadata() {
        return metadata;
    }

    /**
     * A record of the bound rule with its body as one JSON object, for a recorder that assembles its records itself;
     * see {@link #publishRecord}.
     */
    protected final ExecutionRecord newRecord(final long startedAtMillis, final String bodyJson) {
        return new ExecutionRecord(startedAtMillis, content, metadata, bodyJson);
    }

    /**
     * As {@link #newRecord(long, String)}, with rule metadata of the recorder's own: the bound rule's, as
     * {@link #ruleMetadata()} gives it, with what the recorder adds.
     */
    protected final ExecutionRecord newRecord(final long startedAtMillis, final Map<String, String> recordMetadata,
                                              final String bodyJson) {
        return new ExecutionRecord(startedAtMillis, content, recordMetadata, bodyJson);
    }

    /**
     * Publish a record a recorder assembled itself, as TAL's do, under the same record cap.
     */
    protected final void publishRecord(final ExecutionRecord exec) {
        if (exec.isEmpty()) {
            return;
        }
        boolean justCaptured = false;
        synchronized (records) {
            if (captured) {
                return;
            }
            // Stamped under the lock that lists it, so a page read under that lock sees a record captured later at
            // or after its own clock reading, unless the wall clock steps back; page() orders what it reads by time.
            exec.capture(CaptureTime.now());
            records.add(exec);
            if (records.size() >= limits.getRecordCap()) {
                captured = true;
                justCaptured = true;
            }
        }
        if (justCaptured) {
            detachFromHolder();
            onCaptured();
        }
    }

    /**
     * Called once, when the recorder reaches its cap and stops receiving probes; a recorder that assembles records
     * across probes drops what it had in flight here, since the probes that would close those records no longer come.
     */
    protected void onCaptured() {
    }

    /**
     * Self-detach from the bound {@link GateHolder} once this recorder
     * has reached its cap. The holder's {@code removeRecorder} flips the
     * gate off on the {@code 1 → 0} recorder-count transition, so when
     * the last recorder on a holder caps out, every probe call site for
     * that rule returns to the volatile-load-only fast path with no
     * fanOut iteration. Idempotent against the explicit-stop /
     * retention-timeout race — {@link GateHolder#removeRecorder}
     * silently no-ops on a missing recorder.
     */
    private void detachFromHolder() {
        if (boundHolder != null) {
            boundHolder.removeRecorder(this);
        }
    }

    /** Snapshot of every execution record captured so far. Defensive copy — safe to share. */
    public final List<ExecutionRecord> snapshotRecords() {
        synchronized (records) {
            return Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    /**
     * The records captured at or after a client's point, in capture order, up to {@code maxRecords} and
     * {@code maxBytes}. The first one is taken whatever its size, so a page always moves on, and records of one
     * capture time are never split, so the next page, from the time of the first record left out, misses none.
     *
     * @param capturedSince the point the client read from; {@link CaptureTime#EARLIEST} for the first page
     * @param maxRecords    at most this many records, at least 1
     * @param maxBytes      at most this many bytes, as {@code sizeOf} counts them
     * @param sizeOf        a record's size as the page carries it
     */
    public final CapturePage page(final CaptureTime capturedSince, final int maxRecords, final long maxBytes,
                                  final ToLongFunction<ExecutionRecord> sizeOf) {
        final CaptureTime now;
        final boolean capturedNow;
        long totalBytes = 0;
        final List<ExecutionRecord> after = new ArrayList<>();
        synchronized (records) {
            // Read under the lock publishRecord stamps and caps under, so a record listed afterwards is at or after
            // this time, and the cap and size are those of the records read.
            now = CaptureTime.now();
            capturedNow = captured;
            for (final ExecutionRecord r : records) {
                totalBytes += r.byteSize();
                if (r.getCapturedAt().compareTo(capturedSince) >= 0) {
                    after.add(r);
                }
            }
        }
        // Listing order is capture-time order until the wall clock steps back, so the page orders by time before it
        // cuts, or a cut could split one time or leave out a record before the point it returns.
        after.sort(Comparator.comparing(ExecutionRecord::getCapturedAt));
        // Sized outside the lock, since a record is not changed once listed, so probes publishing records never wait
        // on a page being measured.
        final List<ExecutionRecord> page = new ArrayList<>();
        long bytes = 0;
        for (final ExecutionRecord r : after) {
            final long size = sizeOf.applyAsLong(r);
            if (!page.isEmpty() && (page.size() >= maxRecords || bytes + size > maxBytes)
                && !r.getCapturedAt().equals(page.get(page.size() - 1).getCapturedAt())) {
                return new CapturePage(page, r.getCapturedAt(), capturedNow, totalBytes);
            }
            page.add(r);
            bytes += size;
        }
        // A capped recorder captures no more, so its clock bounds nothing: a slower one must not hold back the records
        // other nodes captured after it.
        return new CapturePage(page, capturedNow ? CaptureTime.LATEST : now, capturedNow, totalBytes);
    }

    /** Total bytes (sum of contained sample sizes). Reported in responses; not enforced. */
    public final long totalBytes() {
        synchronized (records) {
            long total = 0;
            for (final ExecutionRecord r : records) {
                total += r.byteSize();
            }
            return total;
        }
    }
}
