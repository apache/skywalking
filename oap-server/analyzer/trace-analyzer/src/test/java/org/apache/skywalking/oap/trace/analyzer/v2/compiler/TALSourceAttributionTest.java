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

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javassist.bytecode.ClassFile;
import javassist.bytecode.SourceFileAttribute;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TestSpanInput;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a stack frame from inside a compiled rule says.
 *
 * <p>The JVM prints a class's {@code SourceFile} attribute in every frame, and for a generated rule that
 * attribute is the only thing naming a file an operator can open — the class name cannot substitute, because
 * sanitising maps {@code /}, {@code -} and {@code .} all to {@code _}. So the attribute has to carry the rule's
 * real path and line. It once carried an invented one, and nothing failed.
 */
class TALSourceAttributionTest {

    @Test
    void theServiceRuleReportsItsOwnLine() throws Exception {
        assertAttribution("otlp-service");
    }

    @Test
    void theSlowSqlRuleReportsItsOwnLine() throws Exception {
        assertAttribution("otlp-slow-sql");
    }

    /**
     * Compile a shipped rule and read back what the JVM will print for it.
     *
     * @param ruleName the rule as the shipped file names it
     */
    private static void assertAttribution(final String ruleName) throws Exception {
        final TALConfig rule = ShippedRules.rule(ruleName);
        final String hint = "attribution_" + ruleName.replace('-', '_');
        // Nothing cleans the dump between runs, so a class left by an earlier run would let this pass on stale
        // output — which is exactly the failure mode the line number had.
        clearPreviousRuns(hint);

        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint(hint);
        generator.setMetricPrefix("otlp");
        generator.setInputType(TestSpanInput.class);
        generator.setSourceRef(ShippedRules.ref(ruleName));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        if (rule.getOutputType() != null) {
            generator.setOutputType(AttributionSlowStatement.class);
        }
        assertNotNull(generator.compile(rule.getDsl()));

        // The generated class is named {yamlFile}_L{line}_{hint}; the line has to be the rule's own.
        final String expectedSimpleName = "otlp_default_L" + rule.getLineNo() + "_" + hint;
        final Path classFile = DslClassOutput.unitTestDir("tal").toPath()
                                             .resolve(expectedSimpleName + ".class");
        assertTrue(Files.isRegularFile(classFile),
            "no class was written as " + expectedSimpleName + "; the line in the name is not the rule's");

        assertEquals(
            "(tal/otlp-default.yaml:" + rule.getLineNo() + ")" + expectedSimpleName + ".java",
            sourceFileOf(classFile),
            "the SourceFile attribute is what a stack frame prints for " + ruleName);
    }

    /**
     * Delete every class this test wrote for {@code hint} on an earlier run, whatever line it claimed, so the
     * assertions below can only be satisfied by what this run produced.
     *
     * @param hint the class-name hint this test compiles under
     */
    private static void clearPreviousRuns(final String hint) throws Exception {
        final Path dir = DslClassOutput.unitTestDir("tal").toPath();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (final Path file : files.filter(f -> f.getFileName().toString().contains(hint))
                                        .collect(java.util.stream.Collectors.toList())) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * @param classFile a generated class
     * @return its {@code SourceFile} attribute
     */
    private static String sourceFileOf(final Path classFile) throws Exception {
        try (InputStream in = Files.newInputStream(classFile);
             DataInputStream data = new DataInputStream(in)) {
            final ClassFile parsed = new ClassFile(data);
            final SourceFileAttribute attribute =
                (SourceFileAttribute) parsed.getAttribute(SourceFileAttribute.tag);
            assertNotNull(attribute, "the generated class carries no SourceFile attribute");
            return attribute.getFileName();
        }
    }
}
