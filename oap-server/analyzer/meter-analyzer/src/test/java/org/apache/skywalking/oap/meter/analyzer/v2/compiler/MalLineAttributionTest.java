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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end guard on the generated artifact's line attribution.
 *
 * <p>The unit tests for {@link DslYamlLineIndex} and {@link MalGeneratedSourceLines} check those
 * pieces in isolation. This one checks the thing that actually ships: that the {@code SourceFile}
 * attribute names the file that was really written, and that every {@code LineNumberTable} entry
 * indexes a line of THAT file which genuinely holds a statement.
 *
 * <p>It exists because the arithmetic converting a method-relative line to a class-relative one
 * depends on the class wrapper's shape — the number of closure fields and whether debug injection
 * is enabled. That is a constant derived by hand, so it needs a test that would fail if the
 * wrapper ever grows a line. Before this work the same code emitted {@code SourceFile} naming a
 * file that never existed and a table of statement ordinals, and nothing noticed.
 */
class MalLineAttributionTest {

    @TempDir
    File outputDir;

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch.resetInjection();
        generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
    }

    @Test
    void sourceFileCarriesTheRuleLocationAndTheGeneratedFileName() throws Exception {
        compileMultiStageRule();

        final File javaFile = writtenFile(".java");
        final ClassFile classFile = readClassFile(writtenFile(".class"));

        // A generated source file exists only under SW_DYNAMIC_CLASS_ENGINE_DEBUG, so in production this
        // attribute cannot usefully name one. It names the RULE, which is what an operator can
        // open, and appends the generated file name so a developer who did dump classes can still
        // find it by name.
        assertEquals("(vm.yaml:37)" + javaFile.getName(), classFile.getSourceFile());
    }

    @Test
    void aNestedRuleKeepsItsPathWhichTheClassNameCannotEncode() throws Exception {
        // The case the class name provably cannot carry: DslJavaSourceText.toIdentifier maps '/', '-' and '.' all
        // to '_', so activemq_activemq_broker could be activemq/activemq-broker,
        // activemq-activemq-broker or activemq_activemq_broker. Only the attribute disambiguates.
        generator.setYamlSource("otel-rules/activemq/activemq-broker.yaml:32");
        generator.setClassNameHint("service_meter");
        generator.compile("meter_activemq_service", "metric.sum(['service'])");

        final ClassFile classFile = readClassFile(writtenFile(".class", "service_meter"));
        assertTrue(classFile.getSourceFile()
                .startsWith("(otel-rules/activemq/activemq-broker.yaml:32)"),
            "the full rule path must survive into SourceFile, got: "
                + classFile.getSourceFile());
    }

    @Test
    void classNameCarriesTheRealYamlLine() throws Exception {
        compileMultiStageRule();

        // 37 is what the caller supplied as the rule's line; it must survive into the label
        // rather than being replaced by the rule's position in the list.
        assertTrue(writtenFile(".class").getName().contains("_L37_"),
            "expected the YAML line in the class name, got: " + writtenFile(".class").getName());
    }

    @Test
    void everyLineNumberEntryPointsAtAStatementInThatFile() throws Exception {
        compileMultiStageRule();

        final List<String> sourceLines =
            Files.readAllLines(writtenFile(".java").toPath(), StandardCharsets.UTF_8);
        final List<Integer> tableLines = lineNumbersOf(readClassFile(writtenFile(".class")), "run");

        assertTrue(tableLines.size() >= 2,
            "expected several boundaries for a multi-stage rule, got " + tableLines.size());

        for (final int line : tableLines) {
            assertTrue(line >= 1 && line <= sourceLines.size(),
                "line " + line + " is outside the generated file (1.." + sourceLines.size() + ")");
            final String text = sourceLines.get(line - 1).trim();
            // A boundary is either a result assignment or the terminating return — never a blank,
            // a brace, or the class declaration, which is what an off-by-N would land on.
            assertTrue(text.endsWith(";"),
                "line " + line + " should hold a statement but was: '" + text + "'");
        }
    }

    @Test
    void theLastBoundaryIsTheReturn() throws Exception {
        compileMultiStageRule();

        final List<String> sourceLines =
            Files.readAllLines(writtenFile(".java").toPath(), StandardCharsets.UTF_8);
        final List<Integer> tableLines = lineNumbersOf(readClassFile(writtenFile(".class")), "run");

        // Pins the ordering end-to-end: the final bytecode boundary must correspond to the final
        // statement of the method, so any drift in the middle would shift this off the return.
        final String last = sourceLines.get(tableLines.get(tableLines.size() - 1) - 1).trim();
        assertTrue(last.startsWith("return "),
            "last boundary should be the return, was: '" + last + "'");
    }

    /** A chain with several stages, so the table has more than one entry to get wrong. */
    private void compileMultiStageRule() throws Exception {
        generator.setClassNameHint("cpu_total");
        generator.setYamlSource("vm.yaml:37");
        try {
            final MalExpression expr = generator.compile(
                "meter_vm_cpu_total",
                "(node_cpu_seconds_total * 100).tagNotEqual('mode', 'idle')"
                    + ".sum(['host']).rate('PT1M')");
            assertNotNull(expr);
        } finally {
            generator.setClassNameHint(null);
            generator.setYamlSource(null);
        }
    }

    private File writtenFile(final String suffix) {
        return writtenFile(suffix, "cpu_total");
    }

    private File writtenFile(final String suffix, final String hint) {
        final File[] matches = outputDir.listFiles(
            (dir, name) -> name.endsWith(suffix) && name.contains(hint));
        assertNotNull(matches, "no files written to " + outputDir);
        assertTrue(matches.length > 0, "no " + suffix + " written to " + outputDir);
        return matches[0];
    }

    private static ClassFile readClassFile(final File file) throws Exception {
        try (DataInputStream in =
                 new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return new ClassFile(in);
        }
    }

    private static List<Integer> lineNumbersOf(final ClassFile classFile, final String methodName) {
        final List<Integer> out = new ArrayList<>();
        for (final MethodInfo method : classFile.getMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            final CodeAttribute code = method.getCodeAttribute();
            if (code == null) {
                continue;
            }
            final LineNumberAttribute table =
                (LineNumberAttribute) code.getAttribute(LineNumberAttribute.tag);
            assertNotNull(table, "no LineNumberTable on " + methodName + "()");
            for (int i = 0; i < table.tableLength(); i++) {
                out.add(table.lineNumber(i));
            }
        }
        return out;
    }
}
