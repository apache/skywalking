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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code "vm.yaml:38"} still crosses module boundaries as one string, so it has a renderer and a
 * parser. Both live on this type, and these tests pin that they agree.
 *
 * <p>They did not, before: the string was concatenated by hand in {@code MetricConvert} and split
 * by hand in two places in {@code MALBytecodeHelper}, each with its own {@code lastIndexOf(':')}.
 * Three independent implementations of one format is how a file and a line drift apart.
 */
class MalSourceRefTest {

    @Test
    void renderAndParseAreInverses() {
        final MalSourceRef anchor = MalSourceRef.ofRule("vm.yaml", 38);
        final MalSourceRef roundTripped = MalSourceRef.parse(anchor.describeYaml());

        assertEquals("vm.yaml:38", anchor.describeYaml());
        assertEquals(anchor.getYamlFile(), roundTripped.getYamlFile());
        assertEquals(anchor.getYamlLine(), roundTripped.getYamlLine());
    }

    @Test
    void anUnresolvedLineSurvivesTheRoundTripAsMinusOneNotZero() {
        // -1 must stay visible: it propagates into the class name as "unknown" and marks a
        // resolution failure worth chasing. A 0 would read as "not applicable" and vanish.
        final MalSourceRef anchor = MalSourceRef.ofRule("vm.yaml", 0);

        assertEquals("vm.yaml:-1", anchor.describeYaml());
        assertEquals(MalSourceRef.UNRESOLVED, MalSourceRef.parse(anchor.describeYaml()).getYamlLine());
        assertEquals("unknown", MalSourceRef.toIdentifierSegment(anchor.getYamlLine()));
    }

    @Test
    void aFileNameWithNoLineOrAGarbageLineDegradesRatherThanThrowing() {
        assertEquals("vm.yaml", MalSourceRef.parse("vm.yaml").getYamlFile());
        assertEquals(MalSourceRef.UNRESOLVED, MalSourceRef.parse("vm.yaml").getYamlLine());
        assertEquals(MalSourceRef.UNRESOLVED, MalSourceRef.parse("vm.yaml:notanumber").getYamlLine());
        assertEquals("vm.yaml", MalSourceRef.parse("vm.yaml:notanumber").getYamlFile());
        assertNull(MalSourceRef.parse(null).getYamlFile());
    }

    @Test
    void completingAnAnchorKeepsTheRuleHalfSoEveryGeneratedFileAgreesOnIt() {
        final MalSourceRef anchor = MalSourceRef.ofRule("vm.yaml", 38);
        final MalSourceRef main = anchor.inGeneratedClass("vm_L38_cpu", 11);
        final MalSourceRef tag = anchor.inGeneratedClass("vm_L38_cpu$_tag", 9);

        // One rule, N generated files: the operator half is shared, the machine half is not.
        assertEquals(main.describeYaml(), tag.describeYaml());
        assertEquals("vm_L38_cpu.java:11", main.describeGenerated());
        assertEquals("vm_L38_cpu$_tag.java:9", tag.describeGenerated());
        // The anchor itself is unchanged — completing it does not mutate it.
        assertNull(anchor.getGeneratedClass());
    }
}
