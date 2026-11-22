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

package org.apache.skywalking.oap.server.dsl.tester.mal;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every expression and filter of every shipped MAL rule file compiles, as the boot composes it (expPrefix, expSuffix).
 * The execution tests cover the expressions their fixtures have data for; this covers the rest, so a compiler change
 * that starts refusing a construct a shipped rule uses fails here rather than at boot.
 */
class ShippedMalRulesCompileTest {
    private static final Path RESOURCES = Path.of(System.getProperty("user.dir")).getParent().getParent()
        .resolve("server-starter/src/main/resources");
    private static final String[] CATALOGS = {
        "otel-rules", "meter-analyzer-config", "log-mal-rules", "envoy-metrics-rules", "telegraf-rules", "zabbix-rules"
    };

    @TestFactory
    Stream<DynamicTest> everyShippedExpressionAndFilterCompiles() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        for (final String catalog : CATALOGS) {
            final List<Path> files;
            try (Stream<Path> walk = Files.walk(RESOURCES.resolve(catalog))) {
                files = walk.filter(f -> f.toString().endsWith(".yaml") || f.toString().endsWith(".yml"))
                            .sorted().collect(Collectors.toList());
            }
            for (final Path file : files) {
                tests.add(DynamicTest.dynamicTest(RESOURCES.relativize(file).toString(), () -> {
                    final Map<String, Object> rule;
                    try (Reader reader = Files.newBufferedReader(file)) {
                        rule = new Yaml().load(reader);
                    }
                    if (rule == null) {
                        // An empty file (log-mal-rules/placeholder.yaml), which Rules skips too
                        return;
                    }
                    final MALClassGenerator generator = new MALClassGenerator(new ClassPool(true));
                    if (rule.get("filter") != null) {
                        generator.compileFilter((String) rule.get("filter"));
                    }
                    // metricsRules, or metrics in the Zabbix format; both are {name, exp}
                    final Object metrics = rule.containsKey("metricsRules") ? rule.get("metricsRules") : rule.get("metrics");
                    assertTrue(metrics instanceof List, "no metricsRules or metrics");
                    for (final Object m : (List<?>) metrics) {
                        final Map<?, ?> metric = (Map<?, ?>) m;
                        generator.compile((String) metric.get("name"), MetricConvert.formatExp(
                            (String) rule.get("expPrefix"), (String) rule.get("expSuffix"), (String) metric.get("exp")));
                    }
                }));
            }
        }
        assertTrue(tests.size() > 50, "shipped MAL rule files found: " + tests.size());
        return tests.stream();
    }
}
