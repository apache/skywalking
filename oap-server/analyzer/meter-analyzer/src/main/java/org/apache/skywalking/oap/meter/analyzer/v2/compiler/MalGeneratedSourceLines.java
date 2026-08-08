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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds the method-body lines that {@code addLineNumberTable}'s bytecode scan will mark as
 * boundaries, so the two agree by construction.
 *
 * <p>The alternative was recording a line at each of the dozen places the codegen appends a
 * statement. That drifts: a new emit site added later silently desynchronises the list from the
 * bytecode, and because the numbers still look plausible the damage is invisible. Deriving both
 * from the same generated text keeps them in lockstep — the source we scan is source we wrote, and
 * it is fully deterministic.
 *
 * <p><b>What counts as a boundary.</b> {@code addLineNumberTable} emits one entry for the first
 * instruction, then one after every store to a result slot. So the boundary lines are, in order:
 * every statement that assigns a result variable, followed by the terminating {@code return}. Any
 * line that emits no such store — notably the {@code MALDebug.captureXxx(...)} probe calls, which
 * are present only when debug injection is enabled — is deliberately skipped. That is exactly why
 * a fixed "statement N is at line N+1" offset cannot be used: enabling injection doubles the
 * source lines without adding boundaries.
 */
final class MalGeneratedSourceLines {

    private MalGeneratedSourceLines() {
    }

    /**
     * @param generatedMethodBody the generated method source, starting at its signature line
     * @return 1-based lines WITHIN THAT METHOD for each statement the bytecode scan will mark,
     *         in order
     */
    static List<Integer> statementLinesOf(final String generatedMethodBody) {
        if (generatedMethodBody == null || generatedMethodBody.isEmpty()) {
            return Collections.emptyList();
        }
        final String[] lines = generatedMethodBody.split("\n", -1);
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            final String trimmed = lines[i].trim();
            if (isResultAssignment(trimmed) || trimmed.startsWith("return ")) {
                // 1-based, and the array index already excludes nothing — line 1 is the signature.
                out.add(i + 1);
            }
        }
        return out;
    }

    /**
     * A result assignment is {@code _var = ...;} or its declaring form {@code SampleFamily _var =
     * ...;}. Both compile to a store into the result slot; a probe call or a bare expression
     * statement does not.
     */
    private static boolean isResultAssignment(final String trimmed) {
        if (!trimmed.endsWith(";") || trimmed.startsWith("return ")) {
            return false;
        }
        final int eq = trimmed.indexOf(" = ");
        if (eq <= 0) {
            return false;
        }
        // The assignment target is the last token before " = ", and every result variable the
        // codegen mints is prefixed with '_' (MALCodegenHelper's variable-per-expression scheme).
        final String lhs = trimmed.substring(0, eq);
        final int lastSpace = lhs.lastIndexOf(' ');
        final String target = lastSpace < 0 ? lhs : lhs.substring(lastSpace + 1);
        return target.startsWith("_");
    }
}
