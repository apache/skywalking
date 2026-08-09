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

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundary list must match, one-for-one and in order, the entries
 * {@code MALBytecodeHelper.addLineNumberTable} derives from the bytecode. A mismatch would put
 * real-looking but wrong line numbers in the table, which is worse than the sequential ordinals
 * it replaces.
 */
class MalGeneratedSourceLinesTest {

    /** Verbatim shape of a real generated run() body — the vm.yaml cpu_total_percentage rule. */
    private static final String RUN_BODY =
        "public SampleFamily run(java.util.Map samples) {\n"                    // 1  signature
            + "  SampleFamily _node_cpu = ((SampleFamily) samples.get(\"x\"));\n"  // 2  declare+store
            + "  _node_cpu = _node_cpu.multiply(Long.valueOf(100L));\n"            // 3  store
            + "  _node_cpu = _node_cpu.tagNotEqual(new String[]{\"m\", \"i\"});\n" // 4  store
            + "  _node_cpu = _node_cpu.sum(java.util.Arrays.asList(new String[]{\"h\"}));\n" // 5
            + "  _node_cpu = _node_cpu.rate(\"PT1M\");\n"                          // 6  store
            + "  _node_cpu = _node_cpu.service(java.util.Arrays.asList(x), y);\n"  // 7  store
            + "  return _node_cpu;\n"                                              // 8  return
            + "}\n";                                                               // 9

    @Test
    void findsEveryAssignmentPlusTheReturn() {
        final List<Integer> lines = MalGeneratedSourceLines.statementLinesOf(RUN_BODY);

        // 6 assignments (lines 2-7) + the return (line 8) = 7 boundaries, matching the 7 entries
        // javap shows for this rule today.
        assertEquals(Arrays.asList(2, 3, 4, 5, 6, 7, 8), lines);
    }

    @Test
    void skipsProbeCallsWhichEmitNoStore() {
        // With SW_DSL_DEBUGGING_INJECTION_ENABLED the codegen interleaves probe calls. They add
        // source lines but NO bytecode boundary, which is precisely why a fixed "statement N is at
        // line N+1" offset cannot be used.
        final String withProbes =
            "public SampleFamily run(java.util.Map samples) {\n"          // 1
                + "  MALDebug.captureInput(this.debug, \"r\", \"m\", _v);\n"  // 2  no store
                + "  SampleFamily _v = ((SampleFamily) samples.get(\"x\"));\n" // 3  store
                + "  MALDebug.captureStage(this.debug, \"r\", \"sum\", _v);\n" // 4  no store
                + "  _v = _v.sum(java.util.Arrays.asList(new String[]{\"h\"}));\n" // 5  store
                + "  return _v;\n"                                            // 6  return
                + "}\n";

        assertEquals(Arrays.asList(3, 5, 6), MalGeneratedSourceLines.statementLinesOf(withProbes));
    }

    @Test
    void ignoresNonResultLocalsAndBareExpressions() {
        final String mixed =
            "public SampleFamily run(java.util.Map samples) {\n"      // 1
                + "  java.util.List other = new java.util.ArrayList();\n" // 2  not a _ target
                + "  other.add(\"x\");\n"                                 // 3  bare expression
                + "  SampleFamily _v = SampleFamily.EMPTY;\n"             // 4  store
                + "  return _v;\n"                                        // 5  return
                + "}\n";

        assertEquals(Arrays.asList(4, 5), MalGeneratedSourceLines.statementLinesOf(mixed));
    }

    @Test
    void emptyInputIsEmptyNotAnError() {
        assertTrue(MalGeneratedSourceLines.statementLinesOf(null).isEmpty());
        assertTrue(MalGeneratedSourceLines.statementLinesOf("").isEmpty());
    }
}
