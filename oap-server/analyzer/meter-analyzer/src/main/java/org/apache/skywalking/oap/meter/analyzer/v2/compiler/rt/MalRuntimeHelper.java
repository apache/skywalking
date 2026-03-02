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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;

/**
 * Static helper methods called by v2-generated {@code MalExpression} classes.
 * Keeps new runtime behaviour in the v2 compiler package, avoiding modifications
 * to the shared {@link SampleFamily} class.
 */
public final class MalRuntimeHelper {

    private MalRuntimeHelper() {
    }

    /**
     * Groovy regex match ({@code =~}): returns a {@code String[][]} where each row is
     * one match with group 0 (full match) and capture groups 1..N.
     * Returns {@code null} if the pattern does not match, so that Groovy-style
     * truthiness checks ({@code matcher ? matcher[0][1] : "unknown"}) work via null check.
     */
    public static String[][] regexMatch(final String input, final String regex) {
        if (input == null) {
            return null;
        }
        final Matcher m = Pattern.compile(regex).matcher(input);
        if (!m.find()) {
            return null;
        }
        final int groupCount = m.groupCount();
        final String[] row = new String[groupCount + 1];
        for (int i = 0; i <= groupCount; i++) {
            row[i] = m.group(i);
        }
        return new String[][] {row};
    }

    /**
     * Reverse division: computes {@code numerator / v} for each sample value {@code v}.
     * Used by generated code for {@code Number / SampleFamily} expressions.
     */
    public static SampleFamily divReverse(final double numerator,
                                          final SampleFamily sf) {
        if (sf == SampleFamily.EMPTY) {
            return SampleFamily.EMPTY;
        }
        final Sample[] original = sf.samples;
        final Sample[] result = new Sample[original.length];
        for (int i = 0; i < original.length; i++) {
            result[i] = original[i].toBuilder()
                .value(numerator / original[i].getValue())
                .build();
        }
        return SampleFamilyBuilder.newBuilder(result).build();
    }
}
