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

package org.apache.skywalking.oap.meter.analyzer.v2;

import java.util.List;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALScriptParser;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MalSourceMap;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MalSourceRef;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.RuleSourceLines;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that a real rule file yields the right YAML line for each stage of a compiled
 * expression — the operator coordinate that the dsl-debugging API reports.
 *
 * <p>This is the case the whole milestone turns on. A rule's compiled expression is a splice of
 * up to three source locations, so "the rule's line" is the right answer for the {@code exp:}
 * body and the WRONG answer for anything contributed by the file-level {@code expPrefix:} or
 * {@code expSuffix:} — wrong in a way that looks correct, because the line it names does exist.
 */
class MalProbeSourceLineTest {

    /** Same shape as a shipped rule file. Line numbers are in the comments and are asserted. */
    private static final String YAML =
        "filter: \"{ tags -> tags.job == 'vm' }\"\n"            // 1
            + "expSuffix: service(['host'], Layer.GENERAL)\n"   // 2
            + "metricPrefix: meter_vm\n"                        // 3
            + "metricsRules:\n"                                 // 4
            + "  - name: cpu_total\n"                           // 5
            + "    exp: node_cpu.sum(['host']).rate('PT1M')\n"  // 6
            + "  - name: mem_used\n"                            // 7
            + "    exp: node_mem.sum(['host'])\n";              // 8

    private static Rule parse(final String yaml) {
        final Rule rule = new Yaml().loadAs(yaml, Rule.class);
        rule.setName("vm");
        RuleSourceLines.assign(rule, yaml);
        return rule;
    }

    /** Rebuilds exactly what MetricConvert does, so the assertions bind to production behaviour. */
    private static MalSourceMap mapFor(final Rule rule, final int ruleIndex) {
        final MetricRuleConfig.RuleConfig r = rule.getMetricsRules().get(ruleIndex);
        final MALScriptParser.PrefixInjection injection =
            MALScriptParser.injectExpPrefixTracked(r.getExp(), rule.getExpPrefix());
        final boolean hasSuffix = rule.getExpSuffix() != null && !rule.getExpSuffix().isEmpty();
        return MalSourceMap.of(injection, hasSuffix,
            r.getExpLine(), rule.getExpPrefixLine(), rule.getExpSuffixLine());
    }

    @Test
    void anchorsComeFromTheFileNotTheRuleOrder() {
        final Rule rule = parse(YAML);

        assertEquals(1, rule.getFilterLine());
        assertEquals(2, rule.getExpSuffixLine());
        // Second rule: its exp is on line 8, not "index 1".
        assertEquals(6, rule.getMetricsRules().get(0).getExpLine());
        assertEquals(8, rule.getMetricsRules().get(1).getExpLine());
        assertEquals(5, rule.getMetricsRules().get(0).getLineNo());
        assertEquals(7, rule.getMetricsRules().get(1).getLineNo());
    }

    @Test
    void stagesInExpReportTheRuleLineAndSuffixStagesReportTheSuffixLine() {
        final Rule rule = parse(YAML);
        final MalSourceMap map = mapFor(rule, 0);
        final String exp = rule.getMetricsRules().get(0).getExp();

        // formatExp = "(" + exp + ")." + expSuffix, so exp offsets shift by 1.
        assertEquals(6, map.yamlLineOf(1 + exp.indexOf("sum")), ".sum() is written in exp:");
        assertEquals(6, map.yamlLineOf(1 + exp.indexOf("rate")), ".rate() is written in exp:");
        // The stage the file-level suffix contributes must NOT claim the rule's line.
        assertEquals(2, map.yamlLineOf(1 + exp.length() + 2), ".service() comes from expSuffix:");
    }

    @Test
    void eachRuleResolvesToItsOwnLine() {
        final Rule rule = parse(YAML);

        assertEquals(6, mapFor(rule, 0).getExpYamlLine());
        assertEquals(8, mapFor(rule, 1).getExpYamlLine());
    }

    @Test
    void prefixDerivedStagesReportThePrefixLine() {
        final String withPrefix =
            "expPrefix: tag({tags -> tags.k = 'v'})\n"          // 1
                + "expSuffix: service(['host'], Layer.GENERAL)\n" // 2
                + "metricPrefix: meter_vm\n"                      // 3
                + "metricsRules:\n"                               // 4
                + "  - name: cpu_total\n"                         // 5
                + "    exp: node_cpu.sum(['host'])\n";            // 6
        final Rule rule = parse(withPrefix);
        assertEquals(1, rule.getExpPrefixLine());

        final MalSourceMap map = mapFor(rule, 0);
        final List<int[]> prefixRanges = MALScriptParser
            .injectExpPrefixTracked(rule.getMetricsRules().get(0).getExp(), rule.getExpPrefix())
            .getPrefixRanges();
        assertTrue(prefixRanges.size() > 0, "prefix should be injected at the metric ref");

        // +1 for the paren formatExp adds ahead of the suffix.
        assertEquals(1, map.yamlLineOf(prefixRanges.get(0)[0] + 1),
            "a stage from expPrefix must report the prefix's line, not the rule's");
    }

    @Test
    void aRuleFileWithoutResolvableLinesReportsUnresolvedNotZero() {
        final Rule rule = new Yaml().loadAs(
            "metricPrefix: m\nmetricsRules:\n  - name: a\n    exp: x.sum(['h'])\n", Rule.class);
        rule.setName("vm");
        // Deliberately NOT stamped — simulates a loader that could not resolve marks.

        assertEquals(MalSourceRef.UNRESOLVED, mapFor(rule, 0).getExpYamlLine());
        assertEquals(MalSourceRef.UNRESOLVED, mapFor(rule, 0).yamlLineOf(0));
    }
}
