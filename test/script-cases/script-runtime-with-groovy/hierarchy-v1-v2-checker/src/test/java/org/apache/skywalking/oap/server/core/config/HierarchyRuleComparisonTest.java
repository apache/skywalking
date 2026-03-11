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

package org.apache.skywalking.oap.server.core.config;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.apache.skywalking.oap.server.core.config.v2.compiler.HierarchyRuleClassGenerator;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dual-path comparison test for hierarchy matching rules.
 * Verifies that Groovy-based rules (v1) produce identical results
 * to pure Java rules (v2) for all service pair combinations.
 *
 * <p>Test pairs are loaded from a companion {@code .data.yaml} file
 * alongside the hierarchy definition YAML.
 */
class HierarchyRuleComparisonTest {

    private static Service svc(final String name, final String shortName) {
        final Service s = new Service();
        s.setName(name);
        s.setShortName(shortName);
        return s;
    }

    private static class TestPair {
        final String description;
        final Service upper;
        final Service lower;
        final Boolean expected;

        TestPair(final String description, final Service upper,
                 final Service lower, final Boolean expected) {
            this.description = description;
            this.upper = upper;
            this.lower = lower;
            this.expected = expected;
        }
    }

    @SuppressWarnings("unchecked")
    @TestFactory
    Collection<DynamicTest> allRulesProduceIdenticalResults() throws Exception {
        final Path hierarchyYml = findHierarchyDefinition();
        final Reader reader = new FileReader(hierarchyYml.toFile());
        final Yaml yaml = new Yaml();
        final Map<String, Map> config = yaml.loadAs(reader, Map.class);
        final Map<String, String> ruleExpressions =
            (Map<String, String>) config.get("auto-matching-rules");

        // Load companion .data.yaml
        final Map<String, List<TestPair>> testPairsByRule = loadInputData(hierarchyYml);

        final GroovyHierarchyRuleProvider groovyProvider = new GroovyHierarchyRuleProvider();

        final Map<String, BiFunction<Service, Service, Boolean>> v1Rules =
            groovyProvider.buildRules(ruleExpressions);

        // Build v2 rules with class output
        final File classBaseDir =
            DslClassOutput.checkerTestDir(hierarchyYml.toFile());
        final String yamlContent = Files.readString(hierarchyYml);
        final String[] yamlLines = yamlContent.split("\n");
        final HierarchyRuleClassGenerator generator = new HierarchyRuleClassGenerator();
        generator.setClassOutputDir(classBaseDir);
        final java.util.Map<String, BiFunction<Service, Service, Boolean>> v2Rules =
            new java.util.HashMap<>();
        for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
            final String ruleName = entry.getKey();
            final int lineNo = findRuleLine(yamlLines, ruleName);
            generator.setClassNameHint(ruleName);
            generator.setYamlSource(lineNo > 0
                ? hierarchyYml.getFileName().toString() + ":" + lineNo
                : hierarchyYml.getFileName().toString());
            v2Rules.put(ruleName, generator.compile(ruleName, entry.getValue()));
        }

        final List<DynamicTest> tests = new ArrayList<>();
        for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
            final String ruleName = entry.getKey();
            final BiFunction<Service, Service, Boolean> v1 = v1Rules.get(ruleName);
            final BiFunction<Service, Service, Boolean> v2 = v2Rules.get(ruleName);

            final List<TestPair> pairs = testPairsByRule.get(ruleName);
            if (pairs == null || pairs.isEmpty()) {
                continue;
            }
            for (final TestPair pair : pairs) {
                tests.add(DynamicTest.dynamicTest(
                    ruleName + " | " + pair.description,
                    () -> {
                        final boolean v1Result = v1.apply(pair.upper, pair.lower);
                        final boolean v2Result = v2.apply(pair.upper, pair.lower);
                        assertEquals(v1Result, v2Result,
                            "Rule '" + ruleName + "' diverged for " + pair.description
                                + ": v1=" + v1Result + ", v2=" + v2Result);
                        if (pair.expected != null) {
                            assertEquals(pair.expected, v1Result,
                                "Rule '" + ruleName + "' expected " + pair.expected
                                    + " for " + pair.description
                                    + " but v1=" + v1Result);
                        }
                    }
                ));
            }
        }
        return tests;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<TestPair>> loadInputData(final Path hierarchyYml) throws Exception {
        final String baseName = hierarchyYml.getFileName().toString()
            .replaceFirst("\\.(yaml|yml)$", "");
        final Path inputPath = hierarchyYml.getParent().resolve(baseName + ".data.yaml");

        final Map<String, List<TestPair>> result = new java.util.HashMap<>();
        if (!Files.isRegularFile(inputPath)) {
            return result;
        }

        final Yaml yaml = new Yaml();
        final String content = Files.readString(inputPath);
        final Map<String, Object> inputConfig = yaml.load(content);
        if (inputConfig == null || !inputConfig.containsKey("input")) {
            return result;
        }

        final Map<String, List<Map<String, Object>>> input =
            (Map<String, List<Map<String, Object>>>) inputConfig.get("input");
        for (final Map.Entry<String, List<Map<String, Object>>> entry : input.entrySet()) {
            final String ruleName = entry.getKey();
            final List<TestPair> pairs = new ArrayList<>();
            for (final Map<String, Object> pairDef : entry.getValue()) {
                final String description = (String) pairDef.getOrDefault("description", "");
                final Map<String, String> upperDef = (Map<String, String>) pairDef.get("upper");
                final Map<String, String> lowerDef = (Map<String, String>) pairDef.get("lower");
                final Boolean expected = pairDef.containsKey("expected")
                    ? (Boolean) pairDef.get("expected") : null;
                final Service upper = svc(
                    upperDef.getOrDefault("name", ""),
                    upperDef.getOrDefault("shortName", ""));
                final Service lower = svc(
                    lowerDef.getOrDefault("name", ""),
                    lowerDef.getOrDefault("shortName", ""));
                pairs.add(new TestPair(description, upper, lower, expected));
            }
            result.put(ruleName, pairs);
        }
        return result;
    }

    /**
     * Find the 1-based line number of {@code ruleName:} in the YAML.
     */
    private static int findRuleLine(final String[] lines, final String ruleName) {
        for (int i = 0; i < lines.length; i++) {
            final String trimmed = lines[i].trim();
            if (trimmed.startsWith(ruleName + ":")) {
                return i + 1;
            }
        }
        return 0;
    }

    private Path findHierarchyDefinition() {
        final String[] candidates = {
            "test/script-cases/scripts/hierarchy-rule/test-hierarchy-definition.yml",
            "../../scripts/hierarchy-rule/test-hierarchy-definition.yml"
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException(
            "Cannot find test-hierarchy-definition.yml in scripts/hierarchy-rule/");
    }
}
