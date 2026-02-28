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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DSLV2Test {

    @Test
    void parseRejectsNullMetricName() {
        assertThrows(UnsupportedOperationException.class, () -> DSL.parse(null, "test_metric"));
    }

    @Test
    void parseThrowsWhenManifestMissing() {
        assertThrows(IllegalStateException.class, () -> DSL.parse("nonexistent_metric", "some_expr"));
    }

    @Test
    void expressionRunWithMalExpression() {
        final MalExpression simple = samples ->
            samples.getOrDefault("test_metric", SampleFamily.EMPTY);

        final Expression expr = new Expression("test_metric", "test_metric", simple);

        // Run with empty map should return fail (EMPTY)
        final Result emptyResult = expr.run(Map.of());
        assertNotNull(emptyResult);
        assertFalse(emptyResult.isSuccess());

        // Run with a real sample should return success
        final Sample sample = Sample.builder()
            .name("test_metric")
            .labels(ImmutableMap.of("service", "svc1"))
            .value(42.0)
            .timestamp(System.currentTimeMillis())
            .build();
        final SampleFamily sf = SampleFamily.build(SampleFamily.RunningContext.instance(), sample);
        final Map<String, SampleFamily> sampleMap = new HashMap<>();
        sampleMap.put("test_metric", sf);

        final Result result = expr.run(sampleMap);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(sf, result.getData());
    }

    @Test
    void filterExpressionWithMalFilter() {
        final MalFilter filter = tags -> "svc1".equals(tags.get("service"));

        final Sample sample1 = Sample.builder()
            .name("metric")
            .labels(ImmutableMap.of("service", "svc1"))
            .value(10.0)
            .timestamp(System.currentTimeMillis())
            .build();
        final Sample sample2 = Sample.builder()
            .name("metric")
            .labels(ImmutableMap.of("service", "svc2"))
            .value(20.0)
            .timestamp(System.currentTimeMillis())
            .build();

        final SampleFamily sf = SampleFamily.build(
            SampleFamily.RunningContext.instance(), sample1, sample2);

        final SampleFamily filtered = sf.filter(filter::test);
        assertNotNull(filtered);
        assertTrue(filtered != SampleFamily.EMPTY);
        assertEquals(1, filtered.samples.length);
        assertEquals(10.0, filtered.samples[0].getValue());
    }
}
