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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.apache.skywalking.oap.server.core.config.v2.compiler.HierarchyRuleClassGenerator;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * JMH benchmark comparing Hierarchy v1 (Groovy) vs v2 (ANTLR4 + Javassist)
 * compilation and execution performance using test-hierarchy-definition.yml
 * (4 matching rules, 23 test pairs).
 *
 * <p>Run: mvn test -pl test/script-cases/script-runtime-with-groovy/hierarchy-v1-v2-checker
 *     -Dtest=HierarchyBenchmark#runBenchmark -DfailIfNoTests=false
 *
 * <h2>Reference results (Apple M3 Max, 128 GB RAM, macOS 26.2, JDK 25)</h2>
 * <pre>
 * Benchmark                     Mode  Cnt     Score      Error  Units
 * HierarchyBenchmark.compileV1  avgt    5  2333.266 ±  285.446  us/op
 * HierarchyBenchmark.compileV2  avgt    5  2482.365 ± 2467.419  us/op
 * HierarchyBenchmark.executeV1  avgt    5     0.958 ±    0.095  us/op
 * HierarchyBenchmark.executeV2  avgt    5     0.370 ±    0.006  us/op
 * </pre>
 *
 * <p>Execute speedup: v2 is ~2.6x faster than v1.
 * Compile times are comparable (only 4 short rules).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class HierarchyBenchmark {

    private Map<String, String> ruleExpressions;

    // Pre-compiled rules for execute benchmarks
    private Map<String, BiFunction<Service, Service, Boolean>> v1Rules;
    private Map<String, BiFunction<Service, Service, Boolean>> v2Rules;

    // Test pairs per rule
    private Map<String, List<ServicePair>> testPairs;

    @Setup(Level.Trial)
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        final Path hierarchyYml = findHierarchyDefinition();
        final Reader reader = new FileReader(hierarchyYml.toFile());
        final Yaml yaml = new Yaml();
        final Map<String, Map> config = yaml.loadAs(reader, Map.class);
        ruleExpressions = (Map<String, String>) config.get("auto-matching-rules");

        // Load test pairs
        testPairs = loadTestPairs(hierarchyYml);

        // Pre-compile for execute benchmarks
        final GroovyHierarchyRuleProvider groovyProvider = new GroovyHierarchyRuleProvider();
        v1Rules = groovyProvider.buildRules(ruleExpressions);

        v2Rules = new HashMap<>();
        final HierarchyRuleClassGenerator gen = new HierarchyRuleClassGenerator();
        for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
            v2Rules.put(entry.getKey(), gen.compile(entry.getKey(), entry.getValue()));
        }
    }

    @Benchmark
    public void compileV1(final Blackhole bh) {
        final GroovyHierarchyRuleProvider provider = new GroovyHierarchyRuleProvider();
        bh.consume(provider.buildRules(ruleExpressions));
    }

    @Benchmark
    public void compileV2(final Blackhole bh) {
        final HierarchyRuleClassGenerator gen = new HierarchyRuleClassGenerator();
        for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
            try {
                bh.consume(gen.compile(entry.getKey(), entry.getValue()));
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void executeV1(final Blackhole bh) {
        for (final Map.Entry<String, BiFunction<Service, Service, Boolean>> entry :
                v1Rules.entrySet()) {
            final List<ServicePair> pairs = testPairs.get(entry.getKey());
            if (pairs == null) {
                continue;
            }
            for (final ServicePair pair : pairs) {
                bh.consume(entry.getValue().apply(pair.upper, pair.lower));
            }
        }
    }

    @Benchmark
    public void executeV2(final Blackhole bh) {
        for (final Map.Entry<String, BiFunction<Service, Service, Boolean>> entry :
                v2Rules.entrySet()) {
            final List<ServicePair> pairs = testPairs.get(entry.getKey());
            if (pairs == null) {
                continue;
            }
            for (final ServicePair pair : pairs) {
                bh.consume(entry.getValue().apply(pair.upper, pair.lower));
            }
        }
    }

    // ==================== Data loading ====================

    @SuppressWarnings("unchecked")
    private Map<String, List<ServicePair>> loadTestPairs(final Path hierarchyYml) throws Exception {
        final String baseName = hierarchyYml.getFileName().toString()
            .replaceFirst("\\.(yaml|yml)$", "");
        final Path dataPath = hierarchyYml.getParent().resolve(baseName + ".data.yaml");
        final Map<String, List<ServicePair>> result = new HashMap<>();
        if (!Files.isRegularFile(dataPath)) {
            return result;
        }
        final Yaml yaml = new Yaml();
        final Map<String, Object> dataConfig = yaml.load(Files.readString(dataPath));
        if (dataConfig == null || !dataConfig.containsKey("input")) {
            return result;
        }
        final Map<String, List<Map<String, Object>>> input =
            (Map<String, List<Map<String, Object>>>) dataConfig.get("input");
        for (final Map.Entry<String, List<Map<String, Object>>> entry : input.entrySet()) {
            final List<ServicePair> pairs = new ArrayList<>();
            for (final Map<String, Object> pairDef : entry.getValue()) {
                final Map<String, String> upperDef =
                    (Map<String, String>) pairDef.get("upper");
                final Map<String, String> lowerDef =
                    (Map<String, String>) pairDef.get("lower");
                pairs.add(new ServicePair(
                    svc(upperDef.getOrDefault("name", ""),
                        upperDef.getOrDefault("shortName", "")),
                    svc(lowerDef.getOrDefault("name", ""),
                        lowerDef.getOrDefault("shortName", ""))
                ));
            }
            result.put(entry.getKey(), pairs);
        }
        return result;
    }

    private static Service svc(final String name, final String shortName) {
        final Service s = new Service();
        s.setName(name);
        s.setShortName(shortName);
        return s;
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

    private static class ServicePair {
        final Service upper;
        final Service lower;

        ServicePair(final Service upper, final Service lower) {
            this.upper = upper;
            this.lower = lower;
        }
    }

    // ==================== JMH launcher ====================

    @Test
    void runBenchmark() throws Exception {
        final Options opt = new OptionsBuilder()
            .include(getClass().getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
