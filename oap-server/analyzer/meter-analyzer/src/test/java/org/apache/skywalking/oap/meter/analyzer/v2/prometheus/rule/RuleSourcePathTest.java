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

import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A Rule reaches the compiler by TWO routes, and only one of them stamps the source path.
 *
 * <ul>
 *   <li>boot — {@code Rules.parseRule} walks the config tree and stamps ruleset dir + relative
 *       path;</li>
 *   <li>runtime-rule hot update — {@code MalFileApplier.parse} does a bare
 *       {@code new Yaml().loadAs(reader, Rule.class)} on content from the database, stamping
 *       nothing.</li>
 * </ul>
 *
 * <p>The second route is the one that broke: {@code @Data} generates a {@code getSourcePath()}
 * that SHADOWS the {@link org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig} default,
 * so an unstamped Rule returned null and produced {@code (null:32)null_L32_x.java}. Every existing
 * test went through the boot route, so none of them saw it.
 */
class RuleSourcePathTest {

    private static final String YAML =
        "filter: \"{ tags -> true }\"\n"
            + "metricPrefix: meter_vm\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: node_cpu.sum(['host'])\n";

    /** Exactly what {@code MalFileApplier.parse} does: load from a string, stamp only the name. */
    private static Rule loadAsRuntimeRuleDoes(final String name) {
        final Rule rule = new Yaml().loadAs(YAML, Rule.class);
        rule.setName(name);
        return rule;
    }

    @Test
    void anUnstampedRuleStillYieldsAUsableSourcePath() {
        final Rule rule = loadAsRuntimeRuleDoes("vm");

        assertNotNull(rule.getSourcePath(),
            "the runtime-rule path never stamps sourcePath; a null here becomes (null:32) in "
                + "every generated class name and SourceFile");
        assertEquals("vm.yaml", rule.getSourcePath());
    }

    @Test
    void noGeneratedCoordinateEverContainsTheLiteralNull() {
        final Rule rule = loadAsRuntimeRuleDoes("vm");

        final String rendered =
            DslSourceRef.ofRule(rule.getSourcePath(), 32).describeYaml();
        assertFalse(rendered.contains("null"),
            "provenance must never render the literal 'null', got: " + rendered);
        assertEquals("vm.yaml:32", rendered);
    }

    @Test
    void aStampedRuleKeepsTheFullPathIncludingTheRulesetDirectory() {
        final Rule rule = loadAsRuntimeRuleDoes("activemq/activemq-broker");
        rule.setSourcePath("otel-rules/activemq/activemq-broker.yaml");

        // The boot route's value must win over the fallback, or nested rules lose their directory.
        assertEquals("otel-rules/activemq/activemq-broker.yaml", rule.getSourcePath());
    }

    @Test
    void aYmlRuleIsNotReportedAsYaml() {
        // Rules.loadRules accepts both extensions. Synthesising ".yaml" for a ".yml" file points
        // the provenance at a path that does not exist on disk.
        final Rule rule = loadAsRuntimeRuleDoes("hierarchy-definition");
        rule.setSourcePath("config/hierarchy-definition.yml");

        assertEquals("config/hierarchy-definition.yml", rule.getSourcePath());
        assertFalse(rule.getSourcePath().endsWith(".yaml"));
    }
}
