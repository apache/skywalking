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
 */

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.counter.CounterWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cross-rule half of the {@link CounterWindow} key, and the companion to
 * {@link BanyanDBErrorRateReproTest}, which pins the within-rule half.
 *
 * <p>Two SHIPPED rules (otel-rules/activemq/activemq-cluster.yaml) read one wire family,
 * {@code java_lang_GarbageCollector_CollectionCount}, tell the generations apart with
 * {@code tagEqual('name', ...)}, and then {@code .sum(['cluster','service_instance_id'])} — which
 * drops the very {@code name} label that distinguished them. Both therefore reduce to the same
 * family plus the same label set, and keying the window on those alone puts them in one slot,
 * where each differences against the other's value. Because the queue is ordered by
 * (timestamp, value) the SMALLER counter wins {@code peek()} and reads a correct 0, so only its
 * partner looks wrong — which is why this survived so long.
 *
 * <p>Note the whole-rule-set comparison suite cannot catch this: it calls
 * {@code CounterWindow.INSTANCE.reset()} before every rule, so each one is evaluated in isolation,
 * the one condition under which the collision cannot appear.
 *
 * <p>Counters are FROZEN across scrapes, so every increase must be exactly 0 for both rules.
 */
public class CrossRuleCounterWindowTest {

    private static final String GROUP_BY = "['cluster','service_instance_id']";

    // Verbatim from otel-rules/activemq/activemq-cluster.yaml (value part).
    private static final String OLD_GC =
        "java_lang_GarbageCollector_CollectionCount.tagEqual('name','PS MarkSweep')"
            + ".sum(" + GROUP_BY + ").increase(\"PT1M\")";
    private static final String YOUNG_GC =
        "java_lang_GarbageCollector_CollectionCount.tagEqual('name','PS Scavenge')"
            + ".sum(" + GROUP_BY + ").increase(\"PT1M\")";

    // Deliberately far apart: a collision differences one against the other, so the error is the
    // gap between them and cannot be mistaken for rounding.
    private static final double OLD_GEN_VALUE = 10;
    private static final double YOUNG_GEN_VALUE = 9000;

    @BeforeEach
    void resetWindow() {
        CounterWindow.INSTANCE.reset();
    }

    private static Sample s(final double value, final long ts, final String gcName) {
        return Sample.builder()
                     .name("java_lang_GarbageCollector_CollectionCount")
                     .labels(ImmutableMap.of(
                         "cluster", "c1", "service_instance_id", "broker-1", "name", gcName))
                     .value(value)
                     .timestamp(ts)
                     .build();
    }

    private static Map<String, SampleFamily> scrape(final long ts) {
        final Map<String, SampleFamily> m = new HashMap<>();
        m.put("java_lang_GarbageCollector_CollectionCount",
              SampleFamilyBuilder.newBuilder(
                  s(OLD_GEN_VALUE, ts, "PS MarkSweep"),
                  s(YOUNG_GEN_VALUE, ts, "PS Scavenge")
              ).build());
        return m;
    }

    private static double maxAbs(final Result r) {
        double max = 0;
        if (r.isSuccess() && r.getData() != SampleFamily.EMPTY) {
            for (final Sample out : r.getData().samples) {
                max = Math.max(max, Math.abs(out.getValue()));
            }
        }
        return max;
    }

    @Test
    void frozenCounters_twoRulesOverOneFamily_bothMustBeZero() {
        final Expression oldGc = DSL.parse(
            "meter_activemq_cluster_gc_parallel_old_collection_count", OLD_GC);
        final Expression youngGc = DSL.parse(
            "meter_activemq_cluster_gc_parallel_young_collection_count", YOUNG_GC);

        final StringBuilder trace = new StringBuilder("\n");
        long ts = 1_700_000_000_000L;
        for (int scrape = 0; scrape < 6; scrape++, ts += 10_000L) {
            // Both rules see the SAME map instance, and BOTH run on this scrape before either
            // sees the next one -- that is what MetricConvert.toMeter does, looping its analyzers
            // per scrape. Do not "simplify" this to run one rule through every scrape and then the
            // other: each rule would then drain its own entry before the other adds one, the
            // collision would not occur, and this test would pass while asserting nothing.
            final Map<String, SampleFamily> in = scrape(ts);
            final double old = maxAbs(oldGc.run(in));
            final double young = maxAbs(youngGc.run(in));
            trace.append("scrape ").append(scrape)
                 .append(" old=").append(old).append(" young=").append(young).append('\n');
            assertEquals(0.0, old, 1e-9, "old-gen increase must be 0 on frozen counters" + trace);
            assertEquals(0.0, young, 1e-9, "young-gen increase must be 0 on frozen counters" + trace);
        }
    }
}
