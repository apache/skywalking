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
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * One full pipeline execution captured by the debug session — a single
 * source/log/SampleFamily walking through a rule's pipeline. Holds the
 * ordered {@link Sample}s the probes accumulated for that execution.
 *
 * <p>Per-execution buffering replaces the previous flat record stream so
 * the operator-facing wire shape mirrors the operator's mental model:
 * "for one event, here's what each pipeline step did". The terminal
 * probe (emit / meterEmit / outputRecord / outputMetric) closes the
 * record; pre-terminal samples accumulate inside.
 */
public final class ExecutionRecord {

    @Getter
    private final long startedAtMillis;
    /**
     * Verbatim rule source as of the moment this record was captured.
     * Per-record (not envelope-level) so a hot-update mid-session that
     * rotates the rule onto a fresh compiled class doesn't make the
     * captured records ambiguous: each record carries the source it
     * actually walked through. Inside a single recorder the content is
     * fixed (the recorder is bound to one GateHolder which carries one
     * snapshot), so all records from the same recorder share the same
     * dsl — but the field belongs to the record so cluster slices stay
     * unambiguous when peers carry different revisions.
     */
    @Getter
    private final String dsl;
    /**
     * Snapshot of the recorder's catalog-specific structured rule
     * metadata at the moment this record was captured. Per-record (not
     * envelope-level) so a hot-update mid-session that rotates the rule
     * onto a new compile keeps the historical metadata visible alongside
     * the new revision.
     *
     * <p>For MAL: {@code {metricPrefix, name, filter, exp, expSuffix}}.
     * For LAL: {@code {outputClass}}. For OAL: empty (the dsl text is
     * already structured).
     */
    @Getter
    private final Map<String, String> metadata;
    private final List<Sample> samples = new ArrayList<>();

    public ExecutionRecord(final long startedAtMillis, final String dsl,
                           final Map<String, String> metadata) {
        this.startedAtMillis = startedAtMillis;
        this.dsl = dsl == null ? "" : dsl;
        this.metadata = metadata == null ? Collections.emptyMap() : metadata;
    }

    /** Append one sample to this execution. Caller must serialise concurrent writes. */
    public void append(final Sample sample) {
        if (sample != null) {
            samples.add(sample);
        }
    }

    public List<Sample> getSamples() {
        return Collections.unmodifiableList(samples);
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    /**
     * Sum of contained sample byte sizes plus the stamped dsl + metadata,
     * for response-side bookkeeping. Approximate (Java char count, not
     * UTF-8 byte count) — the cap is a UI-time bound, not a storage one.
     */
    public long byteSize() {
        long total = dsl == null ? 0 : dsl.length();
        for (final Map.Entry<String, String> e : metadata.entrySet()) {
            total += e.getKey() == null ? 0 : e.getKey().length();
            total += e.getValue() == null ? 0 : e.getValue().length();
        }
        for (final Sample s : samples) {
            total += s.byteSize();
        }
        return total;
    }
}
