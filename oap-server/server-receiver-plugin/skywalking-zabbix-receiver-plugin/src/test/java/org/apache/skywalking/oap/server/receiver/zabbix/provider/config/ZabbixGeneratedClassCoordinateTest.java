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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Files;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated ARTIFACT for a zabbix rule, not merely the config fields.
 *
 * <p>{@link ZabbixConfigSourceCoordinateTest} checks that the loader stamps a path and a line. That
 * is necessary and not sufficient: it would still pass if nothing downstream read those fields.
 * This compiles a rule the way {@code MetricConvert} does — building the coordinate from the REAL
 * {@code ZabbixConfig} getters, which Lombok's {@code @Data} generates OVER the
 * {@code MetricRuleConfig} defaults — and asserts on the class file that comes out.
 *
 * <p>Zabbix reached the compiler with no coordinate at all before this: {@code getSourceName()} was
 * null, so the class carried no {@code _L} segment and a bare {@code SourceFile}.
 */
class ZabbixGeneratedClassCoordinateTest {

    private static final String YAML =
        "metricPrefix: meter_zb\n"                       // 1
            + "metrics:\n"                               // 2
            + "  - name: cpu\n"                          // 3
            + "    exp: agent_cpu.sum(['host'])\n";      // 4

    @TempDir
    File outputDir;

    /** Loads and stamps exactly as ZabbixConfigs.loadConfigs does. */
    private static ZabbixConfig loadAndStamp() {
        final ZabbixConfig config = new Yaml().loadAs(YAML, ZabbixConfig.class);
        config.setSourceName("agent");
        config.setSourcePath("zabbix-rules/agent.yaml");
        final DslYamlLineIndex index = DslYamlLineIndex.index(YAML, "metrics");
        for (int i = 0; i < config.getMetrics().size(); i++) {
            config.getMetrics().get(i).setLineNo(index.rule(i).getEntryLine());
        }
        return config;
    }

    private File compileFirstMetric() throws Exception {
        final ZabbixConfig config = loadAndStamp();
        final ZabbixConfig.Metric metric = config.getMetrics().get(0);

        // The coordinate MetricConvert builds, from the config's own getters.
        final String yamlSource =
            DslSourceRef.ofRule(config.getSourcePath(), metric.getLineNo()).describeYaml();

        final MALClassGenerator generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource(yamlSource);
        generator.setClassNameHint(metric.getName());
        generator.compile("meter_zb_cpu", metric.getExp());

        final File[] classes = outputDir.listFiles(
            (dir, name) -> name.endsWith(".class") && !name.contains("$"));
        assertNotNull(classes, "nothing written to " + outputDir);
        assertEquals(1, classes.length, "expected one generated class");
        return classes[0];
    }

    @Test
    void theClassNameCarriesTheZabbixRuleLine() throws Exception {
        final File generated = compileFirstMetric();

        assertTrue(generated.getName().contains("_L3_"),
            "the metric is on line 3 of agent.yaml; got " + generated.getName());
    }

    @Test
    void theSourceFileNamesTheZabbixRuleFileAndLine() throws Exception {
        final File generated = compileFirstMetric();

        final String sourceFile;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(generated.toPath())))) {
            sourceFile = new ClassFile(in).getSourceFile();
        }
        assertEquals("(zabbix-rules/agent.yaml:3)"
                + generated.getName().replace(".class", ".java"), sourceFile,
            "a zabbix frame must lead back to its rule file, like every other DSL");
    }

    @Test
    void theGeneratedLineIsPresentBecauseZabbixCompilesThroughMal() throws Exception {
        // Zabbix rules are MAL rules — ZabbixConfig implements MetricRuleConfig — so they inherit
        // MAL's per-statement table. This pins that the shared path did not regress it.
        final File generated = compileFirstMetric();

        int entries = 0;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(generated.toPath())))) {
            final ClassFile cf = new ClassFile(in);
            for (final MethodInfo mi : cf.getMethods()) {
                final CodeAttribute code = mi.getCodeAttribute();
                if (code == null) {
                    continue;
                }
                final LineNumberAttribute lna =
                    (LineNumberAttribute) code.getAttribute(LineNumberAttribute.tag);
                if (lna != null) {
                    entries += lna.tableLength();
                }
            }
        }
        assertTrue(entries > 0, "expected a LineNumberTable on the generated zabbix class");
    }
}
