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

package org.apache.skywalking.oap.server.checker.mal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
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
 * JMH benchmark comparing MAL v1 (Groovy) vs v2 (ANTLR4 + Javassist)
 * compilation and execution performance using oap.yaml (56 rules).
 *
 * <p>Run: mvn test -pl test/script-cases/script-runtime-with-groovy/mal-lal-v1-v2-checker
 *     -Dtest=MalBenchmark#runBenchmark -DfailIfNoTests=false
 *
 * <h2>Reference results (Apple M3 Max, 128 GB RAM, macOS 26.2, JDK 25)</h2>
 * <pre>
 * Benchmark               Mode  Cnt      Score       Error  Units
 * MalBenchmark.compileV1  avgt    5  58580.003 ±  5959.853  us/op
 * MalBenchmark.compileV2  avgt    5  62741.101 ± 12124.545  us/op
 * MalBenchmark.executeV1  avgt    5   1838.774 ±   143.389  us/op
 * MalBenchmark.executeV2  avgt    5    376.037 ±    15.169  us/op
 * </pre>
 *
 * <p>Execute speedup: v2 is ~4.9x faster than v1.
 * Compile times are comparable (56 rules, dominated by class generation overhead).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class MalBenchmark {

    private List<RuleEntry> rules;

    // Pre-compiled expressions for execute benchmarks
    private List<org.apache.skywalking.oap.meter.analyzer.dsl.Expression> v1Exprs;
    private List<org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression> v2Exprs;

    // Shared input data
    private Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> v1Data;
    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> v2Data;
    private List<String> ruleNames;

    @Setup(Level.Trial)
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        // Load oap.yaml
        final Path oapYaml = findScript("mal", "test-otel-rules/oap.yaml");
        final Yaml yaml = new Yaml();
        final Map<String, Object> config = yaml.load(Files.readString(oapYaml));

        final String expSuffix = config.containsKey("expSuffix")
            ? (String) config.get("expSuffix") : "";
        final String expPrefix = config.containsKey("expPrefix")
            ? (String) config.get("expPrefix") : "";
        final List<Map<String, String>> metricsRules =
            (List<Map<String, String>>) config.get("metricsRules");

        rules = new ArrayList<>();
        for (final Map<String, String> rule : metricsRules) {
            final String name = rule.get("name");
            final String exp = rule.get("exp");
            if (name != null && exp != null) {
                rules.add(new RuleEntry(name, formatExp(expPrefix, expSuffix, exp)));
            }
        }

        // Load oap.data.yaml
        final Path dataYaml = oapYaml.getParent().resolve("oap.data.yaml");
        final Map<String, Object> dataConfig = yaml.load(Files.readString(dataYaml));
        final Map<String, Object> inputSection =
            (Map<String, Object>) dataConfig.get("input");

        v1Data = buildV1Data(inputSection);
        v2Data = buildV2Data(inputSection);

        // Pre-compile all rules for execute benchmarks
        v1Exprs = new ArrayList<>();
        v2Exprs = new ArrayList<>();
        ruleNames = new ArrayList<>();
        for (final RuleEntry rule : rules) {
            try {
                final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1 =
                    org.apache.skywalking.oap.meter.analyzer.dsl.DSL.parse(
                        rule.name, rule.expression);
                v1.parse();
                v1Exprs.add(v1);

                final MALClassGenerator gen = new MALClassGenerator();
                final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2 =
                    gen.compile(rule.name, rule.expression);
                v2Exprs.add(v2);
                ruleNames.add(rule.name);
            } catch (Exception e) {
                // Skip rules that fail to compile (same as comparison test)
            }
        }

        // Prime CounterWindows for increase/rate expressions
        for (int i = 0; i < v1Exprs.size(); i++) {
            final String name = ruleNames.get(i);
            try {
                v1Exprs.get(i).run(v1Data);
            } catch (Exception ignored) {
            }
            try {
                setMetricName(v2Data, name);
                v2Exprs.get(i).run(v2Data);
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void compileV1(final Blackhole bh) {
        for (final RuleEntry rule : rules) {
            try {
                final org.apache.skywalking.oap.meter.analyzer.dsl.Expression expr =
                    org.apache.skywalking.oap.meter.analyzer.dsl.DSL.parse(
                        rule.name, rule.expression);
                bh.consume(expr.parse());
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void compileV2(final Blackhole bh) {
        for (final RuleEntry rule : rules) {
            try {
                final MALClassGenerator gen = new MALClassGenerator();
                bh.consume(gen.compile(rule.name, rule.expression));
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void executeV1(final Blackhole bh) {
        for (final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1 : v1Exprs) {
            try {
                bh.consume(v1.run(v1Data));
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void executeV2(final Blackhole bh) {
        for (int i = 0; i < v2Exprs.size(); i++) {
            try {
                setMetricName(v2Data, ruleNames.get(i));
                bh.consume(v2Exprs.get(i).run(v2Data));
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== Data builders ====================

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> buildV1Data(
            final Map<String, Object> inputSection) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = System.currentTimeMillis();
        for (final Map.Entry<String, Object> entry : inputSection.entrySet()) {
            final String sampleName = entry.getKey();
            final List<Map<String, Object>> sampleList =
                (List<Map<String, Object>>) entry.getValue();
            final List<org.apache.skywalking.oap.meter.analyzer.dsl.Sample> samples =
                new ArrayList<>();
            for (final Map<String, Object> def : sampleList) {
                final Map<String, String> labels = parseLabels(def);
                samples.add(org.apache.skywalking.oap.meter.analyzer.dsl.Sample.builder()
                    .name(sampleName)
                    .labels(ImmutableMap.copyOf(labels))
                    .value(((Number) def.get("value")).doubleValue())
                    .timestamp(now)
                    .build());
            }
            data.put(sampleName,
                org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder
                    .newBuilder(samples.toArray(
                        new org.apache.skywalking.oap.meter.analyzer.dsl.Sample[0]))
                    .build());
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> buildV2Data(
            final Map<String, Object> inputSection) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = System.currentTimeMillis();
        for (final Map.Entry<String, Object> entry : inputSection.entrySet()) {
            final String sampleName = entry.getKey();
            final List<Map<String, Object>> sampleList =
                (List<Map<String, Object>>) entry.getValue();
            final List<org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample> samples =
                new ArrayList<>();
            for (final Map<String, Object> def : sampleList) {
                final Map<String, String> labels = parseLabels(def);
                samples.add(org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample.builder()
                    .name(sampleName)
                    .labels(ImmutableMap.copyOf(labels))
                    .value(((Number) def.get("value")).doubleValue())
                    .timestamp(now)
                    .build());
            }
            data.put(sampleName,
                org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder
                    .newBuilder(samples.toArray(
                        new org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[0]))
                    .build());
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseLabels(final Map<String, Object> def) {
        final Map<String, String> labels = new HashMap<>();
        final Object raw = def.get("labels");
        if (raw instanceof Map) {
            for (final Map.Entry<String, Object> e :
                    ((Map<String, Object>) raw).entrySet()) {
                labels.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        return labels;
    }

    private static void setMetricName(
            final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data,
            final String name) {
        for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s :
                data.values()) {
            if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                s.context.setMetricName(name);
            }
        }
    }

    // ==================== Utilities ====================

    private static String formatExp(final String expPrefix, final String expSuffix,
                                    final String exp) {
        String ret = exp;
        if (!expPrefix.isEmpty()) {
            final int dot = exp.indexOf('.');
            if (dot >= 0) {
                ret = String.format("(%s.%s)", exp.substring(0, dot), expPrefix);
                final String after = exp.substring(dot + 1);
                if (!after.isEmpty()) {
                    ret = String.format("(%s.%s)", ret, after);
                }
            } else {
                ret = String.format("(%s.%s)", exp, expPrefix);
            }
        }
        if (!expSuffix.isEmpty()) {
            ret = String.format("(%s).%s", ret, expSuffix);
        }
        return ret;
    }

    private static Path findScript(final String language, final String relative) {
        final String[] candidates = {
            "test/script-cases/scripts/" + language + "/" + relative,
            "../../scripts/" + language + "/" + relative
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("Cannot find " + relative + " in scripts/" + language);
    }

    private static class RuleEntry {
        final String name;
        final String expression;

        RuleEntry(final String name, final String expression) {
            this.name = name;
            this.expression = expression;
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
