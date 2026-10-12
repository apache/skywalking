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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TestSpanInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file an OAP actually boots with, {@code tal/otlp-default.yaml}, loaded and compiled as the module loads it.
 *
 * <p>The other compile tests read DSL fragments; this one reads the shipped YAML, so a rule that parses in
 * isolation but is wired into the file wrongly — a bad {@code layer}, an {@code inputType} no provider claims, an
 * {@code outputType} that does not resolve — fails here.
 */
class ShippedTalFileTest {

    @Test
    void theShippedFileParses() throws Exception {
        final TALConfigs configs = load();

        assertEquals("otlp", configs.getMetricPrefix(),
            "the prefix marks these as TAL's; meter_* marks MAL's");
        assertEquals(List.of("otlp-service", "otlp-slow-sql"),
            configs.getRules().stream().map(TALConfig::getName).collect(Collectors.toList()));

        for (final TALConfig rule : configs.getRules()) {
            assertEquals(TALConfig.LAYER_AUTO, rule.getLayer(),
                rule.getName() + " sees the spans that carry no service.layer");
            assertEquals(List.of("otlp"), rule.inputTypes(), rule.getName() + " reads OTLP spans");
            assertNotNull(rule.getDsl(), rule.getName() + " has a body");
        }
        assertEquals("SlowSQL", configs.getRules().get(1).getOutputType());

        // The line a generated class reports is the line an operator opens, so it has to be the rule's own.
        for (final TALConfig rule : configs.getRules()) {
            assertEquals("tal/otlp-default.yaml", rule.getSourcePath());
            assertTrue(rule.getLineNo() > 0, rule.getName() + " carries no line number");
            assertEquals(rule.getName(), nameAtLine(rule.getLineNo()),
                rule.getName() + " is not at the line it claims");
        }
    }

    @Test
    void everyShippedRuleCompiles() throws Exception {
        final TALConfigs configs = load();
        for (final TALConfig rule : configs.getRules()) {
            final TALClassGenerator generator = new TALClassGenerator();
            generator.setClassNameHint("shipped_" + rule.getName().replace('-', '_'));
            generator.setMetricPrefix(configs.getMetricPrefix());
            generator.setInputType(TestSpanInput.class);
            generator.setSourceRef(DslSourceRef.ofRule(rule.getSourcePath(), rule.getLineNo()));
            generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
            if (rule.getOutputType() != null) {
                generator.setOutputType(ShippedSlowStatement.class);
            }
            assertNotNull(generator.compile(rule.getDsl()), rule.getName() + " compiles");
        }
    }

    @Test
    void theServiceRuleDeclaresTheDocumentedMetrics() throws Exception {
        final TALConfigs configs = load();
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("shipped_metric_set");
        generator.setMetricPrefix(configs.getMetricPrefix());
        generator.setInputType(TestSpanInput.class);
        generator.setSourceRef(DslSourceRef.ofRule(
            configs.getRules().get(0).getSourcePath(), configs.getRules().get(0).getLineNo()));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        generator.compile(configs.getRules().get(0).getDsl());

        final List<String> names = generator.getMetricDeclarations().stream()
                                            .map(d -> d.fullName)
                                            .collect(Collectors.toList());
        assertEquals(28, names.size(), "sixteen on the service and its edges, twelve on the conjectured peers and their destinations");
        assertTrue(names.contains("otlp_service_cpm"), names.toString());
        assertTrue(names.contains("otlp_database_access_cpm"), names.toString());
        assertTrue(names.contains("otlp_cache_access_sla"), names.toString());
        assertTrue(names.contains("otlp_mq_access_resp_time"), names.toString());
        assertTrue(names.stream().allMatch(n -> n.startsWith("otlp_")),
            "every metric carries the file's prefix: " + names);
    }

    /**
     * Read the file from the distribution's resources rather than the test classpath: it ships with
     * {@code server-starter}, and a test that copied it would stop testing the file an OAP boots with.
     */
    private static TALConfigs load() throws Exception {
        return ShippedRules.configs();
    }

    /**
     * @param lineNo a 1-based line of the shipped file
     * @return the rule name declared there, for checking a stamped line against the file itself
     */
    private static String nameAtLine(final int lineNo) throws Exception {
        final List<String> lines = Files.readAllLines(ShippedRules.file(), StandardCharsets.UTF_8);
        return lines.get(lineNo - 1).trim().replaceFirst("^- name: ", "");
    }

}
