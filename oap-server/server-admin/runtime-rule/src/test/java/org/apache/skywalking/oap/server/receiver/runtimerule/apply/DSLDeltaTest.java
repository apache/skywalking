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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DSLDeltaTest {

    @Test
    void noChangeCarriesEmptySetsAndReason() {
        final DSLDelta d = DSLDelta.noChange();
        assertEquals(Classification.NO_CHANGE, d.classification());
        assertTrue(d.addedMetrics().isEmpty());
        assertTrue(d.removedMetrics().isEmpty());
        assertTrue(d.shapeBreakMetrics().isEmpty());
        // Reason string is surfaced in HTTP 200 response bodies for observability — asserting
        // on it pins the contract that operator scripts and dashboards parse.
        assertEquals("content byte-identical", d.reason());
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void newRuleReportsAddedMetricsButEmptyAlarmResetSet() {
        // A brand-new bundle has metrics to register but no prior alarm windows to reset —
        // AlarmKernelService.reset on nonexistent metrics is wasted work the design wants to
        // skip, so alarmResetSet() must be empty for NEW regardless of addedMetrics.
        final Set<String> metrics = new HashSet<>();
        metrics.add("meter_vm_cpu");
        metrics.add("meter_vm_mem");
        final DSLDelta d = DSLDelta.newRule(metrics);
        assertEquals(Classification.NEW, d.classification());
        assertEquals(metrics, d.addedMetrics());
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void filterOnlyCarriesCustomReason() {
        final DSLDelta d = DSLDelta.filterOnly("filter expression body changed");
        assertEquals(Classification.FILTER_ONLY, d.classification());
        assertEquals("filter expression body changed", d.reason());
        assertTrue(d.alarmResetSet().isEmpty());
    }

    @Test
    void structuralAlarmResetIsUnionOfAddedRemovedShapeBreak() {
        // Every metric whose semantics moved must have its alarm windows cleared —
        // that's metrics we just created, metrics we just dropped, and metrics whose shape
        // broke under us. Union of the three sets is the authoritative reset target.
        final Set<String> added = setOf("m_new1", "m_new2");
        final Set<String> removed = setOf("m_old");
        final Set<String> shape = setOf("m_shape");
        final DSLDelta d = DSLDelta.structural(added, removed, shape, "cpu scope moved");
        assertEquals(Classification.STRUCTURAL, d.classification());

        final Set<String> expected = new HashSet<>();
        expected.addAll(added);
        expected.addAll(removed);
        expected.addAll(shape);
        assertEquals(expected, d.alarmResetSet());
    }

    @Test
    void structuralAlarmResetDedupsOverlappingMetrics() {
        // A metric that's both "shape-broken" and "added" (because we re-generate the class)
        // would show up twice if we concatenated blindly. The HashSet-union contract prevents
        // duplicate reset calls, which matters for cost (reset walks every running alarm rule).
        final Set<String> added = setOf("m_overlap");
        final Set<String> shape = setOf("m_overlap");
        final DSLDelta d = DSLDelta.structural(added, Collections.emptySet(), shape, "x");
        assertEquals(setOf("m_overlap"), d.alarmResetSet());
    }

    @Test
    void alarmResetSetIsUnmodifiable() {
        // The set is published to AlarmKernelService and iterated by a live thread — if the
        // caller could mutate it after the fact, we would get ConcurrentModificationException
        // mid-reset. Unmodifiable wrapper is the design's cheapest guard.
        final DSLDelta d = DSLDelta.structural(
            setOf("a"), Collections.emptySet(), Collections.emptySet(), "x");
        assertThrows(UnsupportedOperationException.class,
            () -> d.alarmResetSet().add("intruder"));
    }

    @Test
    void nullSetsAreNormalizedToEmpty() {
        // Defensive: callers building DSLDelta from diff code sometimes pass null when a
        // set is absent rather than Collections.emptySet(). Normalizing keeps downstream code
        // free of null checks.
        final DSLDelta d = new DSLDelta(
            Classification.STRUCTURAL, null, null, null, null);
        assertTrue(d.addedMetrics().isEmpty());
        assertTrue(d.removedMetrics().isEmpty());
        assertTrue(d.shapeBreakMetrics().isEmpty());
        assertEquals("", d.reason());
    }

    private static Set<String> setOf(final String... s) {
        final Set<String> r = new HashSet<>();
        Collections.addAll(r, s);
        return r;
    }
}
