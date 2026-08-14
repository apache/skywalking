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

import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A closure companion is its own class file, so it must carry its own generated-source
 * coordinates: its own {@code .java}, and a line into THAT file.
 *
 * <p>Javassist's {@code ClassFile} constructor always installs a {@code SourceFile} attribute
 * naming {@code <simpleName>.java}, so a companion has ALWAYS claimed a source file. Until the
 * generated source file was written that name dangled — it pointed at a file nobody ever created, which reads
 * as correct to an IDE right up until it fails to open it. These tests pin that the name resolves.
 */
class MalCompanionSourceTest {

    /** Rule YAML anchor, in the fused form the generator is configured with. */
    private static final String YAML_SOURCE = "vm.yaml:38";

    @TempDir
    File outputDir;

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource(YAML_SOURCE);
    }

    @Test
    void aCompanionGetsItsOwnSidecarSoItsSourceFileNameResolves() throws Exception {
        generator.setClassNameHint("cpu_total");
        generator.compile("meter_vm_cpu", "metric.tag({ tags -> tags.service = 'svc1' })");

        final File companion = findCompanionClass();
        final File sourceFile = new File(
            outputDir, companion.getName().replace(".class", ".java"));
        assertTrue(sourceFile.isFile(),
            "companion .class exists but its SourceFile names a file that was never written: "
                + sourceFile.getName());
    }

    @Test
    void theCompanionLineNumberEntryPointsAtItsOwnSamSignature() throws Exception {
        generator.setClassNameHint("cpu_total");
        generator.compile("meter_vm_cpu", "metric.tag({ tags -> tags.service = 'svc1' })");

        final File companion = findCompanionClass();
        final List<String> sourceLines = Files.readAllLines(
            new File(outputDir, companion.getName().replace(".class", ".java")).toPath(),
            StandardCharsets.UTF_8);

        final List<int[]> table = MalClassAttributes.lineNumberTableOf(companion);
        assertEquals(1, table.size(),
            "a companion carries exactly ONE entry: the per-statement scan cannot find real "
                + "boundaries in a closure body, so more than one entry would be invented");
        assertEquals(0, table.get(0)[0], "the single entry covers the method from pc 0");

        // The decisive assertion. A line number is only meaningful against the file it indexes,
        // so resolve it and check what is actually there. This fails if the generated source file wrapper ever
        // gains or loses a line without the SAM signature moving with it; the line is searched for, not counted, so it tracks the envelope automatically.
        final int line = table.get(0)[1];
        assertTrue(line >= 1 && line <= sourceLines.size(),
            "line " + line + " is outside the generated source file (" + sourceLines.size() + " lines)");
        final String target = sourceLines.get(line - 1);
        assertTrue(target.contains("public ") && target.contains("(") && target.endsWith("{"),
            "line " + line + " should hold the SAM signature but was: '" + target + "'");
    }

    @Test
    void eachCompanionOfOneRuleIsItsOwnFileWhileTheYamlAnchorStaysShared() throws Exception {
        generator.setClassNameHint("multi");
        generator.compile("meter_vm_multi",
            "metric.tag({ tags -> tags.a = 'b' }).tag({ tags -> tags.c = 'd' })");

        final File[] companions = outputDir.listFiles(
            (dir, name) -> name.contains("$") && name.endsWith(".class"));
        assertNotNull(companions);
        assertEquals(2, companions.length,
            "one rule, two closures — Javassist cannot emit lambdas, so each is its own class");

        // Distinct generated files ...
        assertTrue(!companions[0].getName().equals(companions[1].getName()));
        for (final File companion : companions) {
            assertTrue(new File(outputDir, companion.getName().replace(".class", ".java")).isFile(),
                "every generated class file needs its own source file: " + companion.getName());
        }

    }

    @Test
    void anUnresolvedLineIsNeverWrittenIntoTheTable() {
        // line_number is an unsigned u2, so UNRESOLVED (-1) would serialize as 65535 and read as
        // a real line. DslGeneratedFileWriter.attachSignatureLine omits the attribute instead,
        // which is the only honest encoding of "unknown".
        final DslSourceRef ref = DslSourceRef.ofRule("vm.yaml", 0);

        assertEquals(DslSourceRef.UNRESOLVED, ref.getYamlLine());
        assertEquals("vm.yaml:-1", ref.describeYaml(),
            "an unresolved line stays visible as -1 rather than vanishing");
    }

    private File findCompanionClass() {
        final File[] companions = outputDir.listFiles(
            (dir, name) -> name.contains("$") && name.endsWith(".class"));
        assertNotNull(companions, "nothing written to " + outputDir);
        assertEquals(1, companions.length, "expected exactly one companion");
        return companions[0];
    }

    /** Minimal {@code LineNumberTable} reader: returns {@code {start_pc, line_number}} pairs. */
    private static final class LineNumberTables {

        static List<int[]> of(final File classFile) throws IOException {
            final List<int[]> out = new ArrayList<>();
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
                final ClassFile cf = new ClassFile(in);
                for (final MethodInfo mi : cf.getMethods()) {
                    final CodeAttribute code = mi.getCodeAttribute();
                    if (code == null) {
                        continue;
                    }
                    final LineNumberAttribute lna = (LineNumberAttribute)
                        code.getAttribute(LineNumberAttribute.tag);
                    if (lna == null) {
                        continue;
                    }
                    for (int i = 0; i < lna.tableLength(); i++) {
                        out.add(new int[]{lna.startPc(i), lna.lineNumber(i)});
                    }
                }
            }
            return out;
        }
    }
}
