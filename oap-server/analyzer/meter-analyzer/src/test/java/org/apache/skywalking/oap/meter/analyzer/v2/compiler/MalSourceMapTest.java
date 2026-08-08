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

/**
 * Pins the worked example this whole change exists for: in a rule whose {@code expSuffix} is
 * file-level, the stages written in {@code exp:} must report the rule's line while the suffix's
 * stage reports the SUFFIX's line — not the rule's.
 */
class MalSourceMapTest {

    private static final int EXP_LINE = 38;
    private static final int PREFIX_LINE = 18;
    private static final int SUFFIX_LINE = 32;

    @Test
    void expStagesReportTheRuleLineAndSuffixStagesReportTheSuffixLine() {
        final String exp = "node_cpu.sum(['host'])";
        final MALScriptParser.PrefixInjection injection =
            MALScriptParser.injectExpPrefixTracked(exp, null);
        final MalSourceMap map =
            MalSourceMap.of(injection, true, EXP_LINE, PREFIX_LINE, SUFFIX_LINE);

        // Formatted: "(" + exp + ")." + expSuffix
        //             0    1..22     23,24   25...
        assertEquals(EXP_LINE, map.yamlLineOf(1), "start of exp");
        assertEquals(EXP_LINE, map.yamlLineOf(1 + exp.indexOf("sum")), "a stage inside exp");
        assertEquals(SUFFIX_LINE, map.yamlLineOf(1 + exp.length() + 2), "first char of expSuffix");
        assertEquals(SUFFIX_LINE, map.yamlLineOf(1 + exp.length() + 40), "deeper into expSuffix");
    }

    @Test
    void prefixDerivedStagesReportThePrefixLineNotTheRuleLine() {
        // The case an offset-free implementation gets silently wrong: expPrefix is spliced INSIDE
        // exp, so a naive "before the suffix boundary means exp" rule mis-attributes it.
        final String prefix = "tag({tags -> tags.k = 'v'})";
        final MALScriptParser.PrefixInjection injection =
            MALScriptParser.injectExpPrefixTracked("node_cpu.sum(['host'])", prefix);
        final MalSourceMap map =
            MalSourceMap.of(injection, true, EXP_LINE, PREFIX_LINE, SUFFIX_LINE);

        final int[] prefixRange = injection.getPrefixRanges().get(0);
        // +1 for the wrapping paren formatExp adds when a suffix follows.
        assertEquals(PREFIX_LINE, map.yamlLineOf(prefixRange[0] + 1));
        assertEquals(PREFIX_LINE, map.yamlLineOf(prefixRange[1] - 1 + 1));

        // Immediately after the injected prefix we are back in exp.
        assertEquals(EXP_LINE, map.yamlLineOf(prefixRange[1] + 1 + 1));
    }

    @Test
    void withoutASuffixThereIsNoWrappingParenAndNoSuffixSegment() {
        final String exp = "node_cpu.sum(['host'])";
        final MalSourceMap map = MalSourceMap.of(
            MALScriptParser.injectExpPrefixTracked(exp, null),
            false, EXP_LINE, PREFIX_LINE, SUFFIX_LINE);

        assertEquals(EXP_LINE, map.yamlLineOf(0), "no leading paren, so offset 0 is exp");
        assertEquals(MalSourceRef.UNRESOLVED, map.yamlLineOf(exp.length() + 5),
            "nothing lives past exp when there is no suffix");
    }

    @Test
    void unknownOffsetsAndEmptyMapsResolveToUnresolved() {
        assertEquals(MalSourceRef.UNRESOLVED, MalSourceMap.EMPTY.yamlLineOf(0));
        assertEquals(MalSourceRef.UNRESOLVED,
            MalSourceMap.of(null, true, EXP_LINE, PREFIX_LINE, SUFFIX_LINE).yamlLineOf(0));
        assertEquals(MalSourceRef.UNRESOLVED, MalSourceMap.of(
            MALScriptParser.injectExpPrefixTracked("a.sum(['s'])", null),
            true, EXP_LINE, PREFIX_LINE, SUFFIX_LINE).yamlLineOf(-1));
    }

    @Test
    void anUnresolvedYamlLineStaysUnresolvedRatherThanBecomingZero() {
        // Phase A could not resolve the exp line: the map must not report 0, which downstream
        // would treat as "not applicable" and silently omit.
        final MalSourceMap map = MalSourceMap.of(
            MALScriptParser.injectExpPrefixTracked("a.sum(['s'])", null),
            false, 0, 0, 0);

        assertEquals(MalSourceRef.UNRESOLVED, map.yamlLineOf(0));
    }
}
