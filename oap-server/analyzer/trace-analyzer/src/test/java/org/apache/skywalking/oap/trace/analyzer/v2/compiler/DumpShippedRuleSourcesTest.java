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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TestSpanInput;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the Java that each shipped rule compiles to, for reading.
 *
 * <p>Run it on its own when you want to see what a rule became:
 *
 * <pre>{@code
 * ./mvnw -o -pl oap-server/analyzer/trace-analyzer -Dtest=DumpShippedRuleSourcesTest test
 * }</pre>
 *
 * <p>Output lands in {@code target/tal-rule-generated-classes/}, one {@code .java} and one {@code .class} per
 * rule, named {@code otlp_default_L{line}_{rule}}. The {@code .java} is the verbatim source Javassist compiled —
 * it exists because Javassist compiles from an in-memory string, so there is otherwise no source to read, and
 * because its bytecode tends to defeat decompilers.
 *
 * <p>The directory is this test's own, so the output is the shipped rules and nothing else; the other tests dump
 * their synthetic rules to {@code tal-generated-classes/}. It is cleared on every run, so what is there is what
 * the current rules compile to.
 */
class DumpShippedRuleSourcesTest {
    private static final Logger LOG = LoggerFactory.getLogger(DumpShippedRuleSourcesTest.class);

    /**
     * Its own directory, so a stale class from another test cannot be mistaken for a shipped rule.
     */
    private static final File OUTPUT_DIR = new File("target/tal-rule-generated-classes");

    @Test
    void dumpEveryShippedRule() throws Exception {
        clear();

        final List<TALConfig> rules = ShippedRules.configs().getRules();
        for (final TALConfig rule : rules) {
            final TALClassGenerator generator = new TALClassGenerator();
            // The rule's own name, so the generated class reads as the rule does rather than as a test does.
            generator.setClassNameHint(rule.getName().replace('-', '_'));
            generator.setMetricPrefix(rule.getMetricPrefix());
            generator.setInputType(TestSpanInput.class);
            generator.setSourceRef(ShippedRules.ref(rule.getName()));
            generator.setClassOutputDir(OUTPUT_DIR);
            if (rule.getOutputType() != null) {
                generator.setOutputType(AttributionSlowStatement.class);
            }
            assertNotNull(generator.compile(rule.getDsl()), rule.getName() + " compiles");
        }

        final List<String> written = listing();
        assertEquals(rules.size() * 2, written.size(),
            "one .java and one .class per shipped rule, got " + written);
        for (final TALConfig rule : rules) {
            // Prefix, not equality: DslClassNaming.allocate dedups against a process-wide set, so when another
            // test in the same JVM has already claimed this name the file gains a _2 suffix.
            final String expected = "otlp_default_L" + rule.getLineNo() + "_" + rule.getName().replace('-', '_');
            assertTrue(written.stream().anyMatch(f -> f.startsWith(expected) && f.endsWith(".java")),
                "no source written for " + rule.getName() + "; got " + written);
            assertTrue(written.stream().anyMatch(f -> f.startsWith(expected) && f.endsWith(".class")),
                "no class written for " + rule.getName() + "; got " + written);
        }

        LOG.info("Generated sources for {} shipped rule(s) in {}:\n  {}",
            rules.size(), OUTPUT_DIR.getAbsolutePath(), String.join("\n  ", written));
    }

    private static void clear() throws Exception {
        if (!OUTPUT_DIR.isDirectory()) {
            return;
        }
        try (Stream<Path> files = Files.list(OUTPUT_DIR.toPath())) {
            for (final Path file : files.collect(Collectors.toList())) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static List<String> listing() throws Exception {
        try (Stream<Path> files = Files.list(OUTPUT_DIR.toPath())) {
            return files.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }
}
