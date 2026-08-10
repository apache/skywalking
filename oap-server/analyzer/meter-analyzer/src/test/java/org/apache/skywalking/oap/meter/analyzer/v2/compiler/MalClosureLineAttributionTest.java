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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.RuleSourceLines;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both coordinate spaces, for closures in all three authoring positions.
 *
 * <p>A closure is the one construct where a rule stops being a single file — it becomes its own
 * class — and it is also the construct most often written OUTSIDE the rule's own {@code exp:}. In
 * the shipped rule set a {@code tag({...})} in a file-level {@code expPrefix:} or {@code
 * expSuffix:} is the dominant pattern, not an edge case: {@code otel-rules/istio-controlplane.yaml}
 * carries one on line 31, and every activemq / clickhouse / aws-* rule does the same.
 *
 * <p>That combination is exactly where an implementation resolving per-RULE instead of per-STAGE
 * stays plausible while being wrong: it reports the rule's {@code exp:} line for a closure the
 * operator wrote twenty lines earlier, and the line it names does exist, so nothing looks broken.
 */
class MalClosureLineAttributionTest {

    /**
     * Closures in all three positions, on deliberately distinct lines. If any assertion below
     * could pass by reporting a neighbouring line, the fixture is not doing its job.
     */
    private static final String YAML =
        "expPrefix: tag({tags -> tags.a = 'p'})\n"                                    // 1
            + "expSuffix: tag({tags -> tags.b = 's'}).service(['host'], Layer.GENERAL)\n" // 2
            + "metricPrefix: meter_vm\n"                                                  // 3
            + "metricsRules:\n"                                                           // 4
            + "  - name: cpu\n"                                                           // 5
            + "    exp: node_cpu.tag({tags -> tags.c = 'e'}).sum(['host'])\n";             // 6

    private static final int PREFIX_LINE = 1;
    private static final int SUFFIX_LINE = 2;
    private static final int EXP_LINE = 6;

    @TempDir
    File outputDir;

    private Rule rule;
    private String formatted;

    @BeforeEach
    void setUp() {
        rule = new Yaml().loadAs(YAML, Rule.class);
        rule.setName("vm");
        RuleSourceLines.assign(rule, YAML);

        final MetricRuleConfig.RuleConfig r = rule.getMetricsRules().get(0);
        // Exactly what MetricConvert.formatExp splices together.
        formatted = "(" + MALScriptParser.injectExpPrefix(r.getExp(), rule.getExpPrefix())
            + ")." + rule.getExpSuffix();
    }

    @Test
    void theFixtureItselfAnchorsWhereTheCommentsClaim() {
        assertEquals(PREFIX_LINE, rule.getExpPrefixLine());
        assertEquals(SUFFIX_LINE, rule.getExpSuffixLine());
        assertEquals(EXP_LINE, rule.getMetricsRules().get(0).getExpLine());
    }

    @Test
    void aClosureFromEachPositionGetsItsOwnGeneratedFileAndItsOwnResolvableLine() throws Exception {
        compileFormatted();

        final File[] companions = outputDir.listFiles(
            (dir, name) -> name.contains("$") && name.endsWith(".class"));
        assertNotNull(companions, "nothing written to " + outputDir);
        assertEquals(3, companions.length,
            "one rule, three closures (prefix + exp + suffix) — Javassist cannot emit lambdas, "
                + "so each is its own class file");

        // Completing the matrix: the YAML-line test above proves each POSITION resolves to its own
        // operator line; this proves each position also lands in its own GENERATED file with a
        // resolvable line there. Companions are matched by the marker each closure body writes, so
        // the assertion does not depend on the order codegen happens to assign field names in.
        assertGeneratedCoordinates("put(\"a\"", "expPrefix");
        assertGeneratedCoordinates("put(\"c\"", "exp");
        assertGeneratedCoordinates("put(\"b\"", "expSuffix");
    }

    /**
     * The generated half of one matrix cell: the companion carrying {@code marker} has its own
     * {@code .java}, its {@code SourceFile} names that file, and its single {@code LineNumberTable}
     * entry resolves to the SAM signature inside it.
     */
    private void assertGeneratedCoordinates(final String marker,
                                            final String position) throws Exception {
        File found = null;
        for (final File companion : outputDir.listFiles(
                (dir, name) -> name.contains("$") && name.endsWith(".class"))) {
            final File sourceFile = new File(
                outputDir, companion.getName().replace(".class", ".java"));
            if (sourceFile.isFile() && new String(Files.readAllBytes(sourceFile.toPath()),
                    StandardCharsets.UTF_8).contains(marker)) {
                found = companion;
                break;
            }
        }
        assertNotNull(found, "no companion generated source file carries the " + position + " closure body");

        final File sourceFile = new File(outputDir, found.getName().replace(".class", ".java"));
        // A companion is its own class file, so it carries the rule location plus ITS OWN
        // generated name -- not its parent's, and not Javassist's bare default.
        assertEquals("(vm.yaml:" + EXP_LINE + ")" + found.getName().replace(".class", ".java"),
            MalClassAttributes.sourceFileOf(found),
            "the companion must carry the rule location and its own file name, for " + position);

        final List<int[]> table = MalClassAttributes.lineNumberTableOf(found);
        assertEquals(1, table.size(), "one entry for the " + position + " companion");

        final int line = table.get(0)[1];
        final List<String> lines =
            Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(line >= 1 && line <= lines.size(),
            "line " + line + " is outside the " + position + " companion's own source");
        final String target = lines.get(line - 1);
        assertTrue(target.contains("public ") && target.endsWith("{"),
            "the " + position + " companion's line should hold its SAM signature but was: '"
                + target + "'");
    }

    private void compileFormatted() throws Exception {
        final MALClassGenerator generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource("vm.yaml:" + EXP_LINE);
        generator.setClassNameHint("cpu");
        generator.compile("meter_vm_cpu", formatted);
    }

}
