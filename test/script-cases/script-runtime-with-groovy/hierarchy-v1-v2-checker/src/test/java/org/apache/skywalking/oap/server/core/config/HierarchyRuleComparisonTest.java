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
import org.apache.skywalking.oap.server.core.config.compiler.CompiledHierarchyRuleProvider;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dual-path comparison test for hierarchy matching rules.
 * Verifies that Groovy-based rules (v1) produce identical results
 * to pure Java rules (v2) for all service pair combinations.
 */
class HierarchyRuleComparisonTest {

    private static Service svc(final String name, final String shortName) {
        final Service s = new Service();
        s.setName(name);
        s.setShortName(shortName);
        return s;
    }

    /**
     * Test case: upper service, lower service, and a human-readable description.
     */
    private static class TestPair {
        final String description;
        final Service upper;
        final Service lower;

        TestPair(final String description, final Service upper, final Service lower) {
            this.description = description;
            this.upper = upper;
            this.lower = lower;
        }
    }

    @SuppressWarnings("unchecked")
    @TestFactory
    Collection<DynamicTest> allRulesProduceIdenticalResults() throws Exception {
        final Reader reader = new FileReader(findHierarchyDefinition().toFile());
        final Yaml yaml = new Yaml();
        final Map<String, Map> config = yaml.loadAs(reader, Map.class);
        final Map<String, String> ruleExpressions = (Map<String, String>) config.get("auto-matching-rules");

        final GroovyHierarchyRuleProvider groovyProvider = new GroovyHierarchyRuleProvider();
        final CompiledHierarchyRuleProvider javaProvider = new CompiledHierarchyRuleProvider();

        final Map<String, BiFunction<Service, Service, Boolean>> v1Rules =
            groovyProvider.buildRules(ruleExpressions);
        final Map<String, BiFunction<Service, Service, Boolean>> v2Rules =
            javaProvider.buildRules(ruleExpressions);

        final List<DynamicTest> tests = new ArrayList<>();
        for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
            final String ruleName = entry.getKey();
            final BiFunction<Service, Service, Boolean> v1 = v1Rules.get(ruleName);
            final BiFunction<Service, Service, Boolean> v2 = v2Rules.get(ruleName);

            for (final TestPair pair : testPairsFor(ruleName)) {
                tests.add(DynamicTest.dynamicTest(
                    ruleName + " | " + pair.description,
                    () -> {
                        final boolean v1Result = v1.apply(pair.upper, pair.lower);
                        final boolean v2Result = v2.apply(pair.upper, pair.lower);
                        assertEquals(v1Result, v2Result,
                            "Rule '" + ruleName + "' diverged for " + pair.description
                                + ": v1=" + v1Result + ", v2=" + v2Result);
                    }
                ));
            }
        }
        return tests;
    }

    private static List<TestPair> testPairsFor(final String ruleName) {
        final List<TestPair> pairs = new ArrayList<>();
        switch (ruleName) {
            case "name":
                pairs.add(new TestPair("exact match",
                    svc("my-service", "my-service"),
                    svc("my-service", "my-service")));
                pairs.add(new TestPair("mismatch",
                    svc("svc-a", "svc-a"),
                    svc("svc-b", "svc-b")));
                pairs.add(new TestPair("same shortName different name",
                    svc("svc-a", "same"),
                    svc("svc-b", "same")));
                pairs.add(new TestPair("empty names",
                    svc("", ""),
                    svc("", "")));
                break;

            case "short-name":
                pairs.add(new TestPair("exact shortName match",
                    svc("full-a", "svc"),
                    svc("full-b", "svc")));
                pairs.add(new TestPair("shortName mismatch",
                    svc("a", "svc-1"),
                    svc("b", "svc-2")));
                pairs.add(new TestPair("same name different shortName",
                    svc("same", "short-a"),
                    svc("same", "short-b")));
                pairs.add(new TestPair("empty shortNames",
                    svc("a", ""),
                    svc("b", "")));
                break;

            case "lower-short-name-remove-ns":
                pairs.add(new TestPair("match: svc == svc.namespace",
                    svc("a", "svc"),
                    svc("b", "svc.namespace")));
                pairs.add(new TestPair("match: app == app.default",
                    svc("a", "app"),
                    svc("b", "app.default")));
                pairs.add(new TestPair("no dot in lower",
                    svc("a", "svc"),
                    svc("b", "svc")));
                pairs.add(new TestPair("mismatch prefix",
                    svc("a", "other"),
                    svc("b", "svc.namespace")));
                pairs.add(new TestPair("dot at position 0",
                    svc("a", ""),
                    svc("b", ".namespace")));
                pairs.add(new TestPair("multiple dots - uses last",
                    svc("a", "svc.ns1"),
                    svc("b", "svc.ns1.ns2")));
                pairs.add(new TestPair("empty lower",
                    svc("a", "svc"),
                    svc("b", "")));
                break;

            case "lower-short-name-with-fqdn":
                pairs.add(new TestPair("match: db.svc.cluster.local:3306 vs db",
                    svc("a", "db.svc.cluster.local:3306"),
                    svc("b", "db")));
                pairs.add(new TestPair("match: redis.svc.cluster.local:6379 vs redis",
                    svc("a", "redis.svc.cluster.local:6379"),
                    svc("b", "redis")));
                pairs.add(new TestPair("no colon in upper",
                    svc("a", "db"),
                    svc("b", "db")));
                pairs.add(new TestPair("wrong fqdn suffix",
                    svc("a", "db:3306"),
                    svc("b", "other")));
                pairs.add(new TestPair("upper without fqdn",
                    svc("a", "db:3306"),
                    svc("b", "db")));
                pairs.add(new TestPair("empty upper",
                    svc("a", ""),
                    svc("b", "db")));
                pairs.add(new TestPair("colon at end",
                    svc("a", "db.svc.cluster.local:"),
                    svc("b", "db")));
                break;

            default:
                throw new IllegalArgumentException("Unknown rule: " + ruleName);
        }
        return pairs;
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
