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

package org.apache.skywalking.oap.server.checker.lal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.log.analyzer.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.dsl.LalExpression;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dual-path comparison test for LAL (Log Analysis Language) scripts.
 * For each LAL rule across all LAL YAML files:
 * <ul>
 *   <li>Path A (v1): Verify Groovy compiles the DSL without error</li>
 *   <li>Path B (v2): ANTLR4 + Javassist compilation via {@link LALClassGenerator},
 *        verify it produces a valid {@link LalExpression}</li>
 * </ul>
 */
class LalComparisonTest {

    @TestFactory
    Collection<DynamicTest> lalScriptsCompile() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        final Map<String, List<LalRule>> yamlRules = loadAllLalYamlFiles();

        for (final Map.Entry<String, List<LalRule>> entry : yamlRules.entrySet()) {
            final String yamlFile = entry.getKey();
            for (final LalRule rule : entry.getValue()) {
                tests.add(DynamicTest.dynamicTest(
                    yamlFile + " | " + rule.name,
                    () -> verifyCompilation(rule.name, rule.dsl)
                ));
            }
        }

        return tests;
    }

    private void verifyCompilation(final String ruleName,
                                   final String dsl) throws Exception {
        // ---- V1: Verify Groovy can parse the DSL ----
        try {
            final groovy.lang.GroovyShell sh = new groovy.lang.GroovyShell();
            final groovy.lang.Script script = sh.parse(dsl);
            assertNotNull(script, "V1 Groovy should parse '" + ruleName + "'");
        } catch (Exception e) {
            fail("V1 (Groovy) failed to parse LAL rule '" + ruleName + "': " + e.getMessage());
            return;
        }

        // ---- V2: ANTLR4 + Javassist compilation ----
        try {
            final LALClassGenerator generator = new LALClassGenerator();
            final LalExpression expr = generator.compile(dsl);
            assertNotNull(expr, "V2 should compile '" + ruleName + "'");
        } catch (Exception e) {
            fail("V2 (Java) failed for LAL rule '" + ruleName + "': " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<LalRule>> loadAllLalYamlFiles() throws Exception {
        final java.util.Map<String, List<LalRule>> result = new java.util.HashMap<>();
        final Yaml yaml = new Yaml();

        final Path lalDir = findResourceDir("lal");
        if (lalDir == null) {
            return result;
        }

        final java.io.File[] files = lalDir.toFile().listFiles();
        if (files == null) {
            return result;
        }
        for (final java.io.File file : files) {
            if (!file.getName().endsWith(".yaml") && !file.getName().endsWith(".yml")) {
                continue;
            }
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null || !config.containsKey("rules")) {
                continue;
            }
            final List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("rules");
            if (rules == null) {
                continue;
            }
            final List<LalRule> lalRules = new ArrayList<>();
            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String dslStr = rule.get("dsl");
                if (name == null || dslStr == null) {
                    continue;
                }
                lalRules.add(new LalRule(name, dslStr));
            }
            if (!lalRules.isEmpty()) {
                result.put("lal/" + file.getName(), lalRules);
            }
        }
        return result;
    }

    private Path findResourceDir(final String name) {
        final String[] candidates = {
            "oap-server/server-starter/src/main/resources/" + name,
            "../../../oap-server/server-starter/src/main/resources/" + name
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private static class LalRule {
        final String name;
        final String dsl;

        LalRule(final String name, final String dsl) {
            this.name = name;
            this.dsl = dsl;
        }
    }
}
