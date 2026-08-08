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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the 1-based line contract. SnakeYAML marks are 0-based, so an off-by-one here would
 * silently mis-attribute every generated class label and every debug sample.
 */
class MalYamlLineIndexTest {

    /** Mirrors the shape of a shipped rule file: file-level keys, then a rules sequence. */
    private static final String YAML =
        "# comment line 1\n"                       // 1
            + "\n"                                 // 2
            + "filter: \"{ tags -> true }\"\n"     // 3
            + "expSuffix: service(['host'])\n"     // 4
            + "metricPrefix: meter_vm\n"           // 5
            + "metricsRules:\n"                    // 6
            + "\n"                                 // 7
            + "  # a comment inside the list\n"    // 8
            + "  - name: cpu_total\n"              // 9
            + "    exp: node_cpu.sum(['host'])\n"  // 10
            + "  - name: mem_used\n"               // 11
            + "    exp: node_mem.sum(['host'])\n"; // 12

    @Test
    void resolvesFileLevelAnchors() {
        final MalYamlLineIndex index = MalYamlLineIndex.index(YAML);

        assertEquals(3, index.getFilterLine());
        assertEquals(4, index.getExpSuffixLine());
        // Not declared in this document.
        assertEquals(0, index.getExpPrefixLine());
    }

    @Test
    void resolvesPerRuleAnchorsSkippingBlanksAndComments() {
        final MalYamlLineIndex index = MalYamlLineIndex.index(YAML);

        // The entry anchor is the `- name:` line, not the `metricsRules:` key and not the
        // blank/comment lines between them.
        assertEquals(9, index.rule(0).getEntryLine());
        assertEquals(10, index.rule(0).getExpLine());
        assertEquals(11, index.rule(1).getEntryLine());
        assertEquals(12, index.rule(1).getExpLine());
    }

    @Test
    void outOfRangeRuleIsZeroNotAnError() {
        final MalYamlLineIndex index = MalYamlLineIndex.index(YAML);

        assertEquals(0, index.rule(99).getEntryLine());
        assertEquals(0, index.rule(-1).getExpLine());
    }

    @Test
    void honoursAnAlternateRulesKey() {
        // Zabbix rule files hold their rules under `metrics:` rather than `metricsRules:`.
        final String zabbix = "metricPrefix: meter_zb\n"      // 1
            + "metrics:\n"                                    // 2
            + "  - name: cpu\n"                               // 3
            + "    exp: agent_cpu.sum(['host'])\n";           // 4

        assertEquals(3, MalYamlLineIndex.index(zabbix, "metrics").rule(0).getEntryLine());
        assertEquals(4, MalYamlLineIndex.index(zabbix, "metrics").rule(0).getExpLine());
        // The default key finds nothing in that document — zeros, not an exception.
        assertEquals(0, MalYamlLineIndex.index(zabbix).rule(0).getEntryLine());
    }

    @Test
    void malformedOrEmptyInputDegradesToZeros() {
        // A missing line number must never stop a rule from compiling.
        assertEquals(0, MalYamlLineIndex.index(null).getFilterLine());
        assertEquals(0, MalYamlLineIndex.index("").getFilterLine());
        assertEquals(0, MalYamlLineIndex.index("- not: a mapping root").getFilterLine());
        assertEquals(0, MalYamlLineIndex.index("key: [unclosed").getFilterLine());
    }

    @Test
    void blockScalarsAnchorOnTheirKeyLineNotTheirContent() {
        // Shipped rules use both folded (`>`) and literal (`|-`) scalars, and a real one spans
        // 30 lines (meter-analyzer-config/network-profiling.yaml). Everything the fragment
        // contributes is attributed to the KEY line, which is the stable anchor an operator
        // navigates to — the content lines have no individual meaning to a chain stage.
        final String yaml =
            "expSuffix: |-\n"                    // 1  key
                + "  service(['host'],\n"         // 2  content
                + "          Layer.GENERAL)\n"    // 3  content
                + "metricPrefix: meter_vm\n"      // 4
                + "metricsRules:\n"               // 5
                + "  - name: cpu\n"               // 6
                + "    exp: >\n"                  // 7  key
                + "      node_cpu\n"              // 8  content
                + "        .sum(['host'])\n"      // 9  content
                + "  - name: mem\n"               // 10
                + "    exp: node_mem\n";          // 11

        final MalYamlLineIndex index = MalYamlLineIndex.index(yaml);

        assertEquals(1, index.getExpSuffixLine(), "multi-line expSuffix anchors on its key");
        assertEquals(6, index.rule(0).getEntryLine());
        assertEquals(7, index.rule(0).getExpLine(), "folded exp anchors on its key, not line 8");
        // The rule AFTER a multi-line scalar must not be shifted by the scalar's height.
        assertEquals(10, index.rule(1).getEntryLine());
        assertEquals(11, index.rule(1).getExpLine());
    }
}
