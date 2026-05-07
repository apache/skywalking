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

package org.apache.skywalking.oap.server.receiver.runtimerule.apply;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;

/**
 * Structured result of classifying a new rule bundle against the currently-running one on this
 * node. Carries both the coarse classification and the fine-grained per-metric delta needed
 * by the MAL apply path (per-metric-name shape diff) and by {@code AlarmKernelService.reset}.
 *
 * <p>Immutable. The classifier builds one of these; the apply pipeline consumes it.
 */
public final class DSLDelta {

    private final Classification classification;
    /** Metric names added in the new bundle vs the old one. */
    private final Set<String> addedMetrics;
    /** Metric names present in the old bundle but not the new. */
    private final Set<String> removedMetrics;
    /**
     * Metric names present in both with a shape change (function or scope moved). Trigger the
     * per-metric remove+add sequence in {@link org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem}.
     */
    private final Set<String> shapeBreakMetrics;
    /** Human-readable explanation for log lines and HTTP response bodies. */
    private final String reason;

    public DSLDelta(final Classification classification,
                       final Set<String> addedMetrics,
                       final Set<String> removedMetrics,
                       final Set<String> shapeBreakMetrics,
                       final String reason) {
        this.classification = classification;
        this.addedMetrics = safe(addedMetrics);
        this.removedMetrics = safe(removedMetrics);
        this.shapeBreakMetrics = safe(shapeBreakMetrics);
        this.reason = reason == null ? "" : reason;
    }

    public static DSLDelta noChange() {
        return new DSLDelta(Classification.NO_CHANGE,
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            "content byte-identical");
    }

    public static DSLDelta newRule(final Set<String> metrics) {
        return new DSLDelta(Classification.NEW,
            metrics, Collections.emptySet(), Collections.emptySet(),
            "new (catalog, name) on this node");
    }

    public static DSLDelta filterOnly(final String reason) {
        return new DSLDelta(Classification.FILTER_ONLY,
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), reason);
    }

    public static DSLDelta structural(final Set<String> added,
                                         final Set<String> removed,
                                         final Set<String> shapeBreak,
                                         final String reason) {
        return new DSLDelta(Classification.STRUCTURAL, added, removed, shapeBreak, reason);
    }

    public Classification classification() {
        return classification;
    }

    public Set<String> addedMetrics() {
        return addedMetrics;
    }

    public Set<String> removedMetrics() {
        return removedMetrics;
    }

    public Set<String> shapeBreakMetrics() {
        return shapeBreakMetrics;
    }

    public String reason() {
        return reason;
    }

    /**
     * Set of metric names whose semantics moved and whose alarm windows should therefore be
     * reset at the tail of a successful apply. Empty for FILTER_ONLY and NO_CHANGE; union of
     * {added + removed + shapeBreak} for STRUCTURAL; empty for NEW because no prior windows
     * exist.
     */
    public Set<String> alarmResetSet() {
        if (classification != Classification.STRUCTURAL) {
            return Collections.emptySet();
        }
        final HashSet<String> union = new HashSet<>();
        union.addAll(addedMetrics);
        union.addAll(removedMetrics);
        union.addAll(shapeBreakMetrics);
        return Collections.unmodifiableSet(union);
    }

    private static Set<String> safe(final Set<String> src) {
        return src == null || src.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(src));
    }
}
