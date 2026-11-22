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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The metadata an expression's calls set, nested calls included: one shared context, where the last call to set a
 * value wins.
 */
class MALMetadataExtractorTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    @Test
    void aNestedCallSetsTheMetadata() throws Exception {
        assertTrue(generator.compile("t", "m.plus(other.sum(['svc', 'zone'])).service(['svc'], Layer.GENERAL)")
            .metadata().getAggregationLabels().contains("zone"));
        assertEquals(DownsamplingType.MAX, generator.compile("t",
            "m.plus(other.downsampling(MAX)).service(['svc'], Layer.GENERAL)").metadata().getDownsampling());
        assertEquals(ScopeType.SERVICE, generator.compile("t",
            "m.plus(other.instance(['svc'], ['inst'], Layer.GENERAL)).service(['svc'], Layer.GENERAL)")
            .metadata().getScopeType());
    }

    @Test
    void decorateKeepsItsAttributesOnlyWhereTheChainEndsAtAServiceScope() {
        final String decorated = "m.service(['svc'], Layer.GENERAL).decorate({ me -> me.attr0 = 'x' })";
        final Exception instance = assertThrows(Exception.class, () -> generator.compile("t",
            decorated + ".instance(['svc'], ['ins'], Layer.GENERAL)"));
        assertTrue(String.valueOf(instance.getMessage()).contains("decorate() should be invoked after service()"),
            instance.getMessage());
        final Exception histogram = assertThrows(Exception.class, () -> generator.compile("t",
            decorated + ".histogram()"));
        assertTrue(String.valueOf(histogram.getMessage()).contains("decorate() not supported for histogram metrics"),
            histogram.getMessage());
    }

    @Test
    void theLastCallToSetAValueWins() throws Exception {
        final ExpressionMetadata outer = generator.compile("t",
            "m.plus(other.downsampling(SUM)).service(['svc'], Layer.GENERAL).downsampling(MAX)").metadata();
        assertEquals(DownsamplingType.MAX, outer.getDownsampling());
    }

    @Test
    void countAggregatesByItsLabelList() throws Exception {
        assertTrue(generator.compile("t", "count(m, ['svc', 'zone', 'pod']).service(['svc'], Layer.GENERAL)")
            .metadata().getAggregationLabels().contains("zone"));
    }
}
