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

package org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule;

import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;

/**
 * Stamps YAML source anchors onto a parsed {@link Rule}.
 *
 * <p>Exists so every loader that binds a rule file — the disk loader, the runtime-rule hot-update
 * applier, and the class-generation tooling — assigns lines the same way. They previously agreed
 * only by coincidence: the test harness scanned for {@code name:} text while production used the
 * rules-list index, so the two produced different numbers for the same rule.
 *
 * <p>Callers MUST pass the exact text the rule was bound from. Re-reading the file would risk
 * indexing a different revision than the one in memory.
 */
public final class RuleSourceLines {

    private RuleSourceLines() {
    }

    /**
     * Assign file-level and per-rule anchors. No-op when either argument is null, and individual
     * anchors stay {@code 0} when the document doesn't declare them.
     *
     * @param rule        parsed rule to stamp in place
     * @param yamlContent the exact text {@code rule} was bound from
     */
    public static void assign(final Rule rule, final String yamlContent) {
        if (rule == null || yamlContent == null) {
            return;
        }
        final DslYamlLineIndex index = DslYamlLineIndex.index(yamlContent);
        rule.setFilterLine(index.getFilterLine());
        rule.setExpPrefixLine(index.getExpPrefixLine());
        rule.setExpSuffixLine(index.getExpSuffixLine());

        final List<MetricsRule> rules = rule.getMetricsRules();
        if (rules == null) {
            return;
        }
        // Positional: the index walks the same sequence snakeyaml bound, in the same order.
        for (int i = 0; i < rules.size(); i++) {
            final MetricsRule metricsRule = rules.get(i);
            if (metricsRule == null) {
                continue;
            }
            final DslYamlLineIndex.RuleLines lines = index.rule(i);
            metricsRule.setLineNo(lines.getEntryLine());
            metricsRule.setExpLine(lines.getExpLine());
        }
    }
}
