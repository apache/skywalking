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

import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfigs;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.MetricsRule;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.yaml.snakeyaml.Yaml;

/**
 * Two-path classifier. Given a rule file's old and new content (byte strings), produces a
 * {@link DSLDelta} that drives the dslManager's apply strategy and the alarm-window
 * reset set.
 *
 * <p>Return contract by case:
 * <ul>
 *   <li>{@code oldContent == newContent} (byte-identical) → {@link DSLDelta#noChange}. The
 *       dslManager short-circuits — no compile, no swap, no DB touch.</li>
 *   <li>{@code oldContent == null} (first-time on this node) → {@link DSLDelta#newRule}. The
 *       metric-name set surfaces as {@code addedMetrics}; {@code alarmResetSet()} is empty
 *       because no prior windows exist.</li>
 *   <li>Any other change → {@link DSLDelta#structural}. The added / removed / shape-break
 *       sets drive per-metric remove+add in the applier and the alarm-reset target set in
 *       {@code AlarmKernelService.reset}.</li>
 * </ul>
 *
 * <p>FILTER_ONLY is emitted when every metric name is present in both bundles with identical
 * shape (same {@code (functionName, scopeType)} tuple per {@link MalShapeExtractor}). The
 * dslManager's fast path then skips DDL, alarm reset, and L1/L2 drain.
 *
 * <p>STRUCTURAL is emitted when any metric's shape differs, or when metric names were added /
 * removed between old and new. The {@code shapeBreak} set in {@link DSLDelta} carries the
 * precise set of metrics whose shape moved — driving {@code alarmResetSet()} to the minimal
 * correct target instead of a blanket reset, and feeding the {@code allowStorageChange}
 * guardrail on the REST handler that rejects shape-breaking edits unless explicitly opted in.
 *
 * <p>Shape extraction failure — an unparseable MAL expression on either side — falls back to
 * the safe super-set: STRUCTURAL with every common metric in shape-break. Alarms reset more
 * often than strictly required; one evaluation period is enough to self-heal.
 */
public final class DeltaClassifier {

    private DeltaClassifier() {
    }

    /**
     * Classify a MAL rule-file delta. Parses both YAMLs, enumerates metric names by the same
     * {@code metricPrefix + "_" + ruleName} rule {@code MetricConvert} uses at apply time, and
     * returns the {@link DSLDelta} the dslManager/REST handler should act on.
     *
     * <p>Null {@code newContent} is treated as "removing the bundle" — STRUCTURAL with every
     * previously-owned name in {@code removedMetrics}. Null {@code oldContent} is NEW. If both
     * YAMLs parse but either has no {@code metricsRules}, the enumerated set is empty and the
     * classification reflects the delta (usually STRUCTURAL with nothing added or removed — a
     * degenerate but legal state, e.g. an operator writing a valid-but-empty rules list).
     *
     * @throws IllegalArgumentException when either YAML is malformed; the dslManager catches
     *         this and surfaces it as an apply error rather than losing bundle state.
     */
    public static DSLDelta classifyMal(final String oldContent, final String newContent) {
        if (newContent == null) {
            final Set<String> removed = safeEnumerateMalNames(oldContent);
            return DSLDelta.structural(
                Collections.emptySet(), removed, Collections.emptySet(),
                "bundle removed");
        }
        if (oldContent != null && oldContent.equals(newContent)) {
            return DSLDelta.noChange();
        }
        final Set<String> newMetrics = enumerateMalNames(newContent);
        if (oldContent == null) {
            return DSLDelta.newRule(newMetrics);
        }
        final Set<String> oldMetrics = enumerateMalNames(oldContent);
        final Set<String> added = minus(newMetrics, oldMetrics);
        final Set<String> removed = minus(oldMetrics, newMetrics);
        final Set<String> commonMetrics = intersect(oldMetrics, newMetrics);

        // Per-metric shape diff. For every metric present in both the old and
        // new bundle, extract (functionName, scopeType) from its MAL expression and compare.
        // A shape diff on even one metric => STRUCTURAL with that metric in shape-break; no
        // shape diff across all commons plus no adds/removes => FILTER_ONLY (body tweaks,
        // no storage move). Shape-extract failures (unparseable expression on either side)
        // fall back conservatively to shape-break for that metric.
        final Map<String, MalShapeExtractor.MalShape> oldShapes;
        final Map<String, MalShapeExtractor.MalShape> newShapes;
        try {
            oldShapes = MalShapeExtractor.extract(oldContent);
            newShapes = MalShapeExtractor.extract(newContent);
        } catch (final RuntimeException se) {
            // Extraction threw — fall back to conservative STRUCTURAL with every common
            // metric in shape-break. Safe superset; never reports FILTER_ONLY for a bundle
            // we couldn't fully analyse.
            return DSLDelta.structural(added, removed, commonMetrics,
                "shape extract failed: " + se.getMessage());
        }

        final Set<String> shapeBreak = new LinkedHashSet<>();
        for (final String name : commonMetrics) {
            final MalShapeExtractor.MalShape oldShape = oldShapes.get(name);
            final MalShapeExtractor.MalShape newShape = newShapes.get(name);
            if (oldShape == null || newShape == null || !oldShape.equals(newShape)) {
                // Missing shape on either side = unknown = treat as shape-break (conservative).
                // Mismatch = true shape break.
                shapeBreak.add(name);
            }
        }

        if (added.isEmpty() && removed.isEmpty() && shapeBreak.isEmpty()) {
            // Same metric-name set, same shape for every one. Safe to skip DDL, alarm reset,
            // and L1/L2 drain — the fast path.
            return DSLDelta.filterOnly("body/filter/tag edits only (shapes unchanged)");
        }

        final Set<String> shapeBreakFrozen = shapeBreak.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(shapeBreak);
        final String reason = reasonFor(added, removed, shapeBreakFrozen);
        return DSLDelta.structural(added, removed, shapeBreakFrozen, reason);
    }

    /**
     * Classify a LAL rule-file delta. LAL has no direct metric-name target for alarm windows
     * (rule keys are {@code (layer, ruleName)} pairs, not metric names), so the added/removed/
     * shape-break sets are left empty here. When inline-MAL extraction lands in a follow-up
     * (LAL→MAL chain), those synthetic metric names will flow into the shape-break set and
     * drive {@link DSLDelta#alarmResetSet}.
     *
     * <p>For now this just distinguishes NO_CHANGE vs NEW vs STRUCTURAL based on content
     * identity; the dslManager uses the classification to log the intended path and to avoid
     * re-applying byte-identical LAL content.
     */
    public static DSLDelta classifyLal(final String oldContent, final String newContent) {
        if (newContent == null) {
            return DSLDelta.structural(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                "bundle removed");
        }
        if (oldContent != null && oldContent.equals(newContent)) {
            return DSLDelta.noChange();
        }
        if (oldContent == null) {
            // The Set<String> parameter documents *claimed* rule keys, not metric names; we
            // keep it empty so alarmResetSet() is empty on NEW (matches MAL behaviour — no
            // prior windows existed).
            return DSLDelta.newRule(Collections.emptySet());
        }
        return DSLDelta.structural(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            "LAL content changed");
    }

    /**
     * Extract the set of {@code (layer, ruleName)} keys a LAL file declares. Surfaced here so
     * the dslManager can run its cross-file collision check without re-parsing —
     * {@link LalFileApplier#planKeys} does the same work; this is a lower-dep alternative when
     * we only need the enumerated set, not an applier round-trip.
     */
    /**
     * Detect whether an LAL update moves any rule's {@code outputType} — the FQCN of the
     * {@code AbstractLog} subclass the sink dispatches to. A change here reroutes log records
     * to a different storage-backed subclass, which on BanyanDB means a different measure
     * (and on all backends means any previously-indexed rows for the old type are orphaned).
     * The REST handler's {@code allowStorageChange} guardrail treats a non-empty return as a
     * storage-level edit.
     *
     * <p>Also treats rule additions / removals as "storage-affecting" because the inline-MAL
     * metrics a new/removed rule declares would flow into {@code MeterSystem.removeMetric}
     * and trigger a measure drop on BanyanDB.
     *
     * @return set of rule names whose outputType changed, plus rule names added or removed
     *         between old and new. Empty when neither bundle declares outputType and the
     *         rule key set is identical — the safe path.
     */
    public static Set<String> lalStorageAffectingChanges(final String oldContent, final String newContent) {
        if (oldContent == null || newContent == null) {
            // Either side absent means the whole bundle is being added or removed — the
            // caller already treats this as a major event; a non-empty return here just
            // confirms it at the fine-grained level.
            return oldContent == null && newContent == null
                ? Collections.emptySet()
                : enumerateLalRuleKeys(newContent == null ? oldContent : newContent);
        }
        final Map<String, String> oldOut = lalRuleOutputTypes(oldContent);
        final Map<String, String> newOut = lalRuleOutputTypes(newContent);
        final Set<String> out = new LinkedHashSet<>();
        // Added rules (new side has a key the old side doesn't).
        for (final String key : newOut.keySet()) {
            if (!oldOut.containsKey(key)) {
                out.add(key);
            }
        }
        // Removed rules.
        for (final String key : oldOut.keySet()) {
            if (!newOut.containsKey(key)) {
                out.add(key);
            }
        }
        // Changed outputType on a shared rule.
        for (final Map.Entry<String, String> e : oldOut.entrySet()) {
            final String newVal = newOut.get(e.getKey());
            if (newVal != null && !Objects.equals(nullToEmpty(e.getValue()), nullToEmpty(newVal))) {
                out.add(e.getKey());
            }
        }
        return out.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(out);
    }

    private static Map<String, String> lalRuleOutputTypes(final String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyMap();
        }
        try (StringReader r = new StringReader(content)) {
            final LALConfigs configs = new Yaml().loadAs(r, LALConfigs.class);
            if (configs == null || configs.getRules() == null) {
                return Collections.emptyMap();
            }
            final Map<String, String> out = new LinkedHashMap<>();
            for (final LALConfig c : configs.getRules()) {
                final String layer = c.getLayer() == null || c.getLayer().isEmpty()
                    ? "auto" : c.getLayer();
                out.put(layer + ":" + c.getName(), c.getOutputType());
            }
            return Collections.unmodifiableMap(out);
        } catch (final Throwable t) {
            throw new IllegalArgumentException(
                "LAL YAML parse failure while extracting outputType: " + t.getMessage(), t);
        }
    }

    private static String nullToEmpty(final String s) {
        return s == null ? "" : s;
    }

    public static Set<String> enumerateLalRuleKeys(final String content) {
        final Set<String> out = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return Collections.unmodifiableSet(out);
        }
        try (StringReader r = new StringReader(content)) {
            final LALConfigs configs = new Yaml().loadAs(r, LALConfigs.class);
            if (configs == null || configs.getRules() == null) {
                return Collections.unmodifiableSet(out);
            }
            for (final LALConfig c : configs.getRules()) {
                final String layer = c.getLayer() == null || c.getLayer().isEmpty()
                    ? "auto" : c.getLayer();
                out.add(layer + ":" + c.getName());
            }
        } catch (final Throwable t) {
            throw new IllegalArgumentException(
                "LAL YAML parse failure while enumerating rule keys: " + t.getMessage(), t);
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> safeEnumerateMalNames(final String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return enumerateMalNames(content);
        } catch (final RuntimeException e) {
            // A malformed old-side shouldn't block removal — the bundle we're deleting was
            // parseable once and has a known metric set in the applier's Applied record; the
            // dslManager uses that as ground truth, so a parse failure here is diagnostic only.
            return Collections.emptySet();
        }
    }

    private static Set<String> enumerateMalNames(final String content) {
        try (StringReader reader = new StringReader(content)) {
            final Rule rule = new Yaml().loadAs(reader, Rule.class);
            if (rule == null || rule.getMetricsRules() == null) {
                return Collections.emptySet();
            }
            final Set<String> out = new LinkedHashSet<>();
            final String prefix = rule.getMetricPrefix();
            if (prefix == null) {
                return Collections.emptySet();
            }
            for (final MetricsRule r : rule.getMetricsRules()) {
                if (r.getName() != null) {
                    out.add(prefix + "_" + r.getName());
                }
            }
            return Collections.unmodifiableSet(out);
        } catch (final Throwable t) {
            throw new IllegalArgumentException(
                "MAL YAML parse failure: " + t.getMessage(), t);
        }
    }

    private static Set<String> minus(final Set<String> a, final Set<String> b) {
        final Set<String> r = new LinkedHashSet<>(a);
        r.removeAll(b);
        return r.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(r);
    }

    private static Set<String> intersect(final Set<String> a, final Set<String> b) {
        final Set<String> r = new HashSet<>(a);
        r.retainAll(b);
        return r.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(r);
    }

    private static String reasonFor(final Set<String> added, final Set<String> removed,
                                    final Set<String> shapeBreak) {
        final StringBuilder sb = new StringBuilder();
        if (!added.isEmpty()) {
            sb.append("added=").append(added.size());
        }
        if (!removed.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("removed=").append(removed.size());
        }
        if (!shapeBreak.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("possibly-shape-changed=").append(shapeBreak.size());
        }
        return sb.length() == 0 ? "content changed" : sb.toString();
    }
}
