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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * One step inside a single rule execution. Probes accumulate samples
 * into the active {@link ExecutionRecord} as the rule walks its
 * pipeline; the record is finalised when the terminal probe (emit /
 * sink / outputRecord / abort) fires.
 *
 * <ul>
 *   <li>{@code type} — coarse semantic category for the step:
 *       {@code input} (first event entering the rule),
 *       {@code filter} (filter-clause check, see {@code continueOn}),
 *       {@code function} (chain transform: MAL sum/tag/etc., LAL parser/extractor statement),
 *       {@code aggregation} (OAL terminal aggregation function),
 *       {@code output} (terminal emit / sink / outputRecord). Lets the
 *       UI group / colour samples without parsing {@code sourceText}.</li>
 *   <li>{@code sourceText} — verbatim DSL fragment for this step
 *       ({@code from(ServiceRelation.*)}, {@code .filter(detectPoint == DetectPoint.SERVER)},
 *       {@code .cpm()}, {@code tag stage: 'extractor'}). Pulled from the
 *       ANTLR Interval slice at parse time, byte-identical to the
 *       operator's source.</li>
 *   <li>{@code continueOn} — did the rule keep going past this step?
 *       {@code true} when the pipeline continued, {@code false} when
 *       the step short-circuited (filter dropped, parser aborted,
 *       extractor abort()).</li>
 *   <li>{@code payloadJson} — pre-built JSON string of the step's data
 *       state. Built by {@link org.apache.skywalking.oap.server.core.dsldebug.ToJson#toJson}
 *       on the underlying domain object (ISource / SampleFamily /
 *       ExecutionContext). No recorder-side reflection.</li>
 *   <li>{@code sourceLine} — 1-based source line of this step in the
 *       rule file. {@code 0} when the step doesn't map to a single
 *       line; the REST response omits the field in that case (the
 *       parent {@link ExecutionRecord#getDsl()} carries the verbatim
 *       rule source for cross-reference).</li>
 *   <li>{@code reason} — human-readable explanation of WHY the step
 *       stopped the pipeline (a parse exception, a non-matching regexp,
 *       an input-type mismatch). Optional and null on steps that
 *       continued or that stopped without a recorded cause (e.g. an
 *       explicit {@code abort {}}). Only LAL populates it today; the
 *       shared 5-arg constructor is unchanged so MAL / OAL recorders
 *       leave it null.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public final class Sample {
    // Coarse semantic step type, shared across all three DSL recorders. Each DSL emits the
    // subset that fits its pipeline shape (recorders in the sibling lal/ mal/ oal/ packages):
    /** First event entering the rule. Used by LAL, MAL, OAL. */
    public static final String TYPE_INPUT = "input";
    /** Filter-clause check (see {@code continueOn}). Used by MAL and OAL; LAL has no filter step. */
    public static final String TYPE_FILTER = "filter";
    /** Chain transform — MAL sum/tag/etc., LAL parser/extractor statement. Used by LAL, MAL, OAL. */
    public static final String TYPE_FUNCTION = "function";
    /** Terminal aggregation function. OAL only. */
    public static final String TYPE_AGGREGATION = "aggregation";
    /** Terminal emit / sink / outputRecord. Used by LAL, MAL, OAL. */
    public static final String TYPE_OUTPUT = "output";

    private final String type;
    private final String sourceText;
    private final boolean continueOn;
    private final String payloadJson;
    private final int sourceLine;

    /** Why this step stopped the pipeline; null when it continued or has no recorded cause.
     *  Non-final optional add-on so the shared constructor stays 5-arg — see class Javadoc. */
    @Setter
    private String reason;

    /**
     * Char count this sample contributes to the session's reported {@code totalBytes}.
     * Reporting only — there is no byte-budget enforcement; sessions are bounded by
     * {@link SessionLimits#getRecordCap()} (and the per-node active-session ceiling).
     */
    public long byteSize() {
        long size = 0;
        if (type != null) {
            size += type.length();
        }
        if (sourceText != null) {
            size += sourceText.length();
        }
        if (payloadJson != null) {
            size += payloadJson.length();
        }
        if (reason != null) {
            size += reason.length();
        }
        return size;
    }
}
