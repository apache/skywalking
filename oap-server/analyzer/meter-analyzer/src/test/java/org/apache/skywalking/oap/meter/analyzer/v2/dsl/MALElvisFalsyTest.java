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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Groovy's Elvis `?:` applies the fallback when the primary is falsy — including the empty string.
 * The v2 codegen previously emitted Optional.ofNullable(P).orElse(F), which only fires on null, so an
 * empty-string primary (e.g. a label that .sum() filled with "" for an absent key) leaked "" instead
 * of the fallback. This is the exact mechanism behind BanyanDB liaison instances storing node_type=""
 * instead of "n/a".
 */
public class MALElvisFalsyTest {

    private static String tagAfterElvis(final String nodeTypeValue) {
        final ImmutableMap<String, String> labels = nodeTypeValue == null
            ? ImmutableMap.of("svc", "s")
            : ImmutableMap.of("svc", "s", "node_type", nodeTypeValue);
        final SampleFamily sf = SampleFamilyBuilder.newBuilder(
            Sample.builder().name("metric").labels(labels).value(1.0).timestamp(1L).build()).build();
        final Expression expr = DSL.parse("test_elvis",
            "metric.tag({tags -> tags['nt'] = tags.node_type ?: 'n/a'})");
        final Result r = expr.run(Map.of("metric", sf));
        return r.getData().samples[0].getLabels().get("nt");
    }

    private static String tagAfterSideEffectingElvis(final String nodeTypeValue) {
        final SampleFamily sf = SampleFamilyBuilder.newBuilder(
            Sample.builder()
                  .name("metric")
                  .labels(ImmutableMap.of("svc", "s", "node_type", nodeTypeValue))
                  .value(1.0)
                  .timestamp(1L)
                  .build()).build();
        final Expression expr = DSL.parse("test_elvis_remove",
            "metric.tag({tags -> tags['nt'] = tags.remove('node_type') ?: 'n/a'})");
        final Result r = expr.run(Map.of("metric", sf));
        return r.getData().samples[0].getLabels().get("nt");
    }

    @Test
    void emptyStringPrimary_usesFallback() {
        assertEquals("n/a", tagAfterElvis(""), "empty-string primary must fall through to 'n/a' (Groovy-falsy)");
    }

    @Test
    void absentPrimary_usesFallback() {
        assertEquals("n/a", tagAfterElvis(null), "absent (null) primary must fall through to 'n/a'");
    }

    @Test
    void nonEmptyPrimary_keptAsIs() {
        assertEquals("hot", tagAfterElvis("hot"), "non-empty primary must be kept");
    }

    @Test
    void sideEffectingPrimary_evaluatedOnce() {
        assertEquals("hot", tagAfterSideEffectingElvis("hot"),
            "Elvis must not evaluate the primary twice; tags.remove(...) returns a value only once");
    }

    @Test
    void runtimeTruthiness_matchesGroovyFalsyValues() {
        assertEquals("fallback", elvisOf("Integer.parseInt(tags.n)", ImmutableMap.of("n", "0")));
        assertEquals("fallback", elvisOf("Double.parseDouble(tags.n)", ImmutableMap.of("n", "0.0")));
        assertEquals("fallback", elvisOf("tags", ImmutableMap.of()), "an empty map");
        assertEquals("fallback", elvisOf("tags.s.split(',')", ImmutableMap.of("s", ",")), "an empty array");
        assertEquals("-1", elvisOf("Integer.parseInt(tags.n)", ImmutableMap.of("n", "-1")));
        assertEquals("[value]", elvisOf("Arrays.asList(tags.s)", ImmutableMap.of("s", "value")));
    }

    private static String elvisOf(final String primary, final ImmutableMap<String, String> labels) {
        final SampleFamily sf = SampleFamilyBuilder.newBuilder(
            Sample.builder().name("metric").labels(labels).value(1.0).timestamp(1L).build()).build();
        final Expression expr = DSL.parse("test_elvis_truth",
            "metric.tag({tags -> tags['nt'] = String.valueOf(" + primary + " ?: 'fallback')})");
        return expr.run(Map.of("metric", sf)).getData().samples[0].getLabels().get("nt");
    }
}
