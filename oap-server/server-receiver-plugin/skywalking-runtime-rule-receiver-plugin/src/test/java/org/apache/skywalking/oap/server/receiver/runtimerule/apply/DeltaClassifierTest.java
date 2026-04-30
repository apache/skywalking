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

import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaClassifierTest {

    private static final String MAL_TWO_METRICS =
        "metricPrefix: meter_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: cpu_seconds.sum(['host'])\n"
            + "  - name: mem\n"
            + "    exp: mem_bytes.sum(['host'])\n";

    private static final String MAL_TWO_METRICS_BODY_CHANGE =
        "metricPrefix: meter_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: cpu_seconds.sum(['host']).rate('PT1M')\n"
            + "  - name: mem\n"
            + "    exp: mem_bytes.sum(['host'])\n";

    private static final String MAL_ONE_METRIC =
        "metricPrefix: meter_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: cpu_seconds.sum(['host'])\n";

    private static final String MAL_WITH_EXTRA_METRIC =
        "metricPrefix: meter_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: cpu_seconds.sum(['host'])\n"
            + "  - name: mem\n"
            + "    exp: mem_bytes.sum(['host'])\n"
            + "  - name: disk\n"
            + "    exp: disk_bytes.sum(['host'])\n";

    @Test
    void byteIdenticalReturnsNoChange() {
        // Sanity — the dslManager's hash short-circuit already catches this before calling us,
        // but the classifier must return NO_CHANGE for the same case so any direct caller
        // (REST handler, tests) gets identical semantics.
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, MAL_TWO_METRICS);
        assertEquals(Classification.NO_CHANGE, d.classification());
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void oldNullIsNew() {
        // First apply on this node (or after a previous unregister). All new metric names are
        // reported as {@code addedMetrics}, but {@code alarmResetSet()} is empty because no
        // prior windows existed to reset — matches {@link DSLDelta#newRule} where
        // alarmResetSet() is deliberately empty for first-apply cases.
        final DSLDelta d = DeltaClassifier.classifyMal(null, MAL_TWO_METRICS);
        assertEquals(Classification.NEW, d.classification());
        assertEquals(setOf("meter_vm_cpu", "meter_vm_mem"), d.addedMetrics());
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void newNullIsStructuralRemoval() {
        // The classifier's contract for "bundle is going away": every prior metric name lands
        // in removedMetrics, and alarmResetSet() contains them so the kernel clears windows
        // whose subjects just vanished. Used by /delete and status→INACTIVE paths.
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, null);
        assertEquals(Classification.STRUCTURAL, d.classification());
        assertEquals(setOf("meter_vm_cpu", "meter_vm_mem"), d.removedMetrics());
        assertEquals(setOf("meter_vm_cpu", "meter_vm_mem"), d.alarmResetSet());
    }

    @Test
    void metricAddedIsStructural() {
        // One metric added, two unchanged (identical shape on the common two). Shape diff
        // correctly keeps them out of the shape-break set now that per-metric extraction is
        // in place — only "disk" lands in added, shape-break is empty.
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, MAL_WITH_EXTRA_METRIC);
        assertEquals(Classification.STRUCTURAL, d.classification());
        assertEquals(setOf("meter_vm_disk"), d.addedMetrics());
        assertTrue(d.removedMetrics().isEmpty());
        assertTrue(d.shapeBreakMetrics().isEmpty(),
            "cpu and mem have unchanged shapes; only the newly-added metric is structural");
        assertTrue(d.alarmResetSet().contains("meter_vm_disk"));
    }

    @Test
    void metricRemovedIsStructural() {
        // One metric dropped ("mem"). removedMetrics carries it — the dslManager / applier
        // calls MeterSystem.removeMetric for every name in this set.
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, MAL_ONE_METRIC);
        assertEquals(Classification.STRUCTURAL, d.classification());
        assertEquals(setOf("meter_vm_mem"), d.removedMetrics());
        assertTrue(d.addedMetrics().isEmpty());
        assertTrue(d.alarmResetSet().contains("meter_vm_mem"));
    }

    @Test
    void bodyOnlyChangeIsFilterOnly() {
        // FILTER_ONLY fast path: same metric names, same (functionName, scopeType) for every
        // metric — only the expression body of "cpu" changed (added .rate('PT1M')). Shape
        // extraction confirms both metrics still resolve to the same storage class, so the
        // dslManager swaps Analyzers without MeterSystem.removeMetric + create round-trip
        // and without any alarm window reset.
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, MAL_TWO_METRICS_BODY_CHANGE);
        assertEquals(Classification.FILTER_ONLY, d.classification());
        assertTrue(d.alarmResetSet().isEmpty(),
            "FILTER_ONLY must not drive alarm windows off — shapes unchanged");
    }

    @Test
    void scopeChangeIsStructuralWithShapeBreak() {
        // Swap the expSuffix from service(...) to instance(...) — same metric names,
        // different scope type. Shape diff on every metric → STRUCTURAL with every common
        // metric in shape-break → alarm reset targets them all.
        final String withInstanceScope = MAL_TWO_METRICS.replace(
            "service(['host'], Layer.OS_LINUX)",
            "instance(['host'], Layer.OS_LINUX)");
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, withInstanceScope);
        assertEquals(Classification.STRUCTURAL, d.classification());
        assertEquals(setOf("meter_vm_cpu", "meter_vm_mem"), d.shapeBreakMetrics());
        assertEquals(setOf("meter_vm_cpu", "meter_vm_mem"), d.alarmResetSet());
    }

    @Test
    void downsamplingFunctionChangeIsStructural() {
        // Explicit .downsampling(SUM) on cpu — same metric name, different storage-side
        // downsampling type. cpu ends up in shape-break; mem (unchanged, default AVG) stays
        // out. The storage-level change is exactly what the allowStorageChange guardrail on
        // the REST handler keys off to reject dangerous edits unless explicitly approved.
        final String withExplicitSum = MAL_TWO_METRICS.replace(
            "exp: cpu_seconds.sum(['host'])",
            "exp: cpu_seconds.sum(['host']).downsampling(SUM)");
        final DSLDelta d = DeltaClassifier.classifyMal(MAL_TWO_METRICS, withExplicitSum);
        assertEquals(Classification.STRUCTURAL, d.classification());
        assertEquals(setOf("meter_vm_cpu"), d.shapeBreakMetrics());
    }

    @Test
    void malformedYamlThrowsOnNewSide() {
        // Malformed new content is unrecoverable — the caller must surface an apply error.
        assertThrows(IllegalArgumentException.class,
            () -> DeltaClassifier.classifyMal(MAL_TWO_METRICS, "this: is: not: valid: yaml"));
    }

    @Test
    void malformedOldContentIsToleratedOnRemoval() {
        // If the prior content somehow became unparseable (race, corruption), classifying
        // against null newContent still succeeds. The removed set comes up empty — the caller
        // falls back to MalFileApplier.Applied.getRegisteredMetricNames for the authoritative
        // prior metric list, so this degradation is safe.
        final DSLDelta d = DeltaClassifier.classifyMal("garbage: not: valid", null);
        assertEquals(Classification.STRUCTURAL, d.classification());
        // Empty because safeEnumerateMalNames swallowed the parse failure on the old side.
        assertTrue(d.removedMetrics().isEmpty());
    }

    @Test
    void lalByteIdenticalIsNoChange() {
        final String lal = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final DSLDelta d = DeltaClassifier.classifyLal(lal, lal);
        assertEquals(Classification.NO_CHANGE, d.classification());
    }

    @Test
    void lalOldNullIsNew() {
        final String lal = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final DSLDelta d = DeltaClassifier.classifyLal(null, lal);
        assertEquals(Classification.NEW, d.classification());
        // LAL NEW carries an empty set today — alarm reset doesn't target LAL rule keys
        // directly, and inline-MAL extraction is a follow-up.
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void lalChangedIsStructural() {
        final String a = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String b = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { json {} sink {} }'\n";
        final DSLDelta d = DeltaClassifier.classifyLal(a, b);
        assertEquals(Classification.STRUCTURAL, d.classification());
    }

    @Test
    void enumerateLalRuleKeysHandlesAutoLayer() {
        // The "auto" layer is stored as null in LALConfig.LAYER_AUTO (empty string on disk).
        // enumerateLalRuleKeys must canonicalize it to "auto" so the cross-file collision
        // check in the dslManager compares auto rules across files correctly.
        final String lal = "rules:\n"
            + "  - name: r1\n"
            + "    layer: MESH\n"
            + "    dsl: 'filter { sink {} }'\n"
            + "  - name: r2\n"
            + "    layer: auto\n"
            + "    dsl: 'filter { sink {} }'\n";
        final Set<String> keys = DeltaClassifier.enumerateLalRuleKeys(lal);
        assertEquals(setOf("MESH:r1", "auto:r2"), keys);
    }

    @Test
    void enumerateLalRuleKeysOnEmptyReturnsEmpty() {
        assertTrue(DeltaClassifier.enumerateLalRuleKeys("").isEmpty());
        assertTrue(DeltaClassifier.enumerateLalRuleKeys(null).isEmpty());
    }

    @Test
    void lalStorageAffectingIdenticalContentIsEmpty() {
        final String lal = "rules:\n  - name: r1\n    layer: MESH\n    outputType: org.apache.skywalking.oap.server.core.source.LogBuilder\n    dsl: 'filter { sink {} }'\n";
        assertTrue(DeltaClassifier.lalStorageAffectingChanges(lal, lal).isEmpty());
    }

    @Test
    void lalStorageAffectingDetectsOutputTypeChange() {
        // Changing outputType on a rule is exactly the "dangerous" case the REST handler
        // allowStorageChange guardrail keys off — rerouting logs to a different AbstractLog
        // subclass means previously-indexed rows for the old subclass are now orphaned and
        // on BanyanDB the new subclass's measure is a separate target.
        final String a = "rules:\n  - name: r1\n    layer: MESH\n    outputType: org.example.TypeA\n    dsl: 'filter { sink {} }'\n";
        final String b = "rules:\n  - name: r1\n    layer: MESH\n    outputType: org.example.TypeB\n    dsl: 'filter { sink {} }'\n";
        final Set<String> affected = DeltaClassifier.lalStorageAffectingChanges(a, b);
        assertEquals(setOf("MESH:r1"), affected);
    }

    @Test
    void lalStorageAffectingDetectsRuleAddRemove() {
        // Rule added (even with no outputType) still counts as storage-affecting because
        // any inline metrics {} it declares would become live MAL metrics, and the mirror
        // case — rule removed — drops the corresponding metric via MeterSystem.removeMetric
        // and the BanyanDB measure along with it.
        final String a = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String b = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n"
            + "  - name: r2\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final Set<String> added = DeltaClassifier.lalStorageAffectingChanges(a, b);
        assertEquals(setOf("MESH:r2"), added);
        final Set<String> removed = DeltaClassifier.lalStorageAffectingChanges(b, a);
        assertEquals(setOf("MESH:r2"), removed);
    }

    @Test
    void lalStorageAffectingDslBodyChangeIsSafe() {
        // Body tweak inside the DSL with same rule keys and same outputType — the guardrail
        // must not flag this as storage-affecting. Operators frequently edit filter / sink
        // bodies to change extraction rules, and blocking those by default would turn the
        // guardrail into a nuisance.
        final String a = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String b = "rules:\n  - name: r1\n    layer: MESH\n    dsl: 'filter { json {} sink {} }'\n";
        assertTrue(DeltaClassifier.lalStorageAffectingChanges(a, b).isEmpty());
    }

    private static Set<String> setOf(final String... s) {
        final Set<String> out = new LinkedHashSet<>();
        Collections.addAll(out, s);
        return out;
    }
}
