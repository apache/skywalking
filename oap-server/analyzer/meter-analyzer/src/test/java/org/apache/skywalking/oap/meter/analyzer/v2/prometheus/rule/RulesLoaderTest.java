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

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls {@link Rules#loadRules} — the real boot loader — rather than re-performing its stamping.
 *
 * <p>{@link RuleSourcePathTest} covers the value-object half: what a Rule yields once its fields
 * are set, including the Lombok-shadowing fallback. It cannot see whether the loader sets them.
 * This does, so reverting {@code Rules.parseRule} fails here rather than passing quietly.
 */
class RulesLoaderTest {

    private static final String DIR = "loader-probe";
    private static final String RULE = "probe-rule";

    private static Rule loadViaProductionLoader() throws Exception {
        final List<Rule> rules = Rules.loadRules(DIR, Collections.singletonList(RULE));
        assertEquals(1, rules.size(), "expected the probe rule file to load");
        return rules.get(0);
    }

    @Test
    void theLoaderStampsTheRulesetQualifiedPathWithItsRealExtension() throws Exception {
        final Rule rule = loadViaProductionLoader();

        // Ruleset directory included, extension preserved. Both were wrong before: nested rules
        // lost their directory, and a .yml rule was reported as .yaml.
        assertEquals(DIR + "/" + RULE + ".yaml", rule.getSourcePath());
    }

    @Test
    void theLoaderStampsEachRuleWithItsOwnEntryLine() throws Exception {
        final Rule rule = loadViaProductionLoader();
        final List<? extends MetricRuleConfig.RuleConfig> metricsRules = rule.getMetricsRules();

        assertEquals(2, metricsRules.size());
        // Lines of the `- name:` anchors in probe-rule.yaml.
        assertEquals(21, metricsRules.get(0).getLineNo());
        assertEquals(23, metricsRules.get(1).getLineNo(),
            "the second rule must carry its own line, not the first's or the file's");
        assertNotEquals(metricsRules.get(0).getLineNo(), metricsRules.get(1).getLineNo());
    }

    @Test
    void theLoaderStampsTheFileLevelFilterLine() throws Exception {
        final Rule rule = loadViaProductionLoader();

        // The filter compiles to its own class, so it needs its own coordinate — distinct from
        // any rule's.
        assertEquals(18, rule.getFilterLine());
        assertTrue(rule.getFilterLine() != rule.getMetricsRules().get(0).getLineNo());
    }
}
