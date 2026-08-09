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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the source-attribution contract from a dedicated, TEST-ONLY LAL script.
 *
 * <p>{@code source-attribution.yaml} is not a copy of any shipped config: the bundled LAL rules
 * change with feature work, and pinning generated-file geometry to one of them would break for
 * reasons unrelated to attribution.
 *
 * <p>Its three rules differ in GENERATED SHAPE — none, one, and two extractor blocks — because the
 * shape is what any line number would depend on: each extractor becomes a private method emitted
 * ahead of {@code execute()}, shifting everything after it. That variation is the point. A single
 * simple rule would exercise only one of the two call sites that used to attach a line table, and
 * would pass even if the other still emitted ordinals.
 */
class LALSourceAttributionScriptTest {

    private static final String SCRIPT =
        "scripts/lal/test-lal/feature-cases/source-attribution.yaml";

    private List<String> dsls;

    @TempDir
    File outputDir;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void loadScript() throws Exception {
        final Path path = Paths.get("src/test/resources").resolve(SCRIPT);
        assertTrue(Files.isRegularFile(path), "dedicated LAL script missing: " + path);

        final Map<String, Object> doc = new Yaml().load(
            new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        final List<Map<String, Object>> rules = (List<Map<String, Object>>) doc.get("rules");
        assertTrue(rules != null && rules.size() >= 3,
            "the script should carry several differently-shaped rules");

        dsls = new ArrayList<>();
        for (final Map<String, Object> rule : rules) {
            dsls.add((String) rule.get("dsl"));
        }
    }

    @Test
    void everyRuleInTheScriptCompilesAndItsSourceFileResolves() throws Exception {
        final List<File> generated = compileAll();
        assertEquals(dsls.size(), generated.size(),
            "each rule should produce one main class");

        for (final File classFile : generated) {
            final String sourceFile = sourceFileOf(classFile);
            assertEquals(classFile.getName().replace(".class", ".java"), sourceFile,
                "SourceFile must name the class's own sidecar");
            assertTrue(new File(classFile.getParentFile(), sourceFile).isFile(),
                "SourceFile names a file that was never written: " + sourceFile);
        }
    }

    @Test
    void noGeneratedMethodClaimsALineItCannotSubstantiate() throws Exception {
        // The regression this pins: line numbers used to be statement ORDINALS (1, 2, 3...). While
        // SourceFile named a nonexistent file that was inert. Once SourceFile resolves, an ordinal
        // resolves too — line 1 of the sidecar is its synthetic comment header, which an IDE would
        // present as the frame's source. Absent beats confidently wrong.
        for (final File classFile : compileAll()) {
            final List<int[]> table = lineNumberTableOf(classFile);
            assertTrue(table.isEmpty(),
                "expected no LineNumberTable for " + classFile.getName() + " but found "
                    + table.size() + " entries, first pointing at line "
                    + (table.isEmpty() ? "-" : String.valueOf(table.get(0)[1])));
        }
    }

    @Test
    void theShapesReallyDoDifferSoTheGeometryIsExercised() throws Exception {
        // Guards the fixture itself. If all three rules compiled to the same shape, the test above
        // would be checking one code path three times while appearing to cover three.
        final List<File> generated = compileAll();
        // File.listFiles() has no defined order, so comparing first against last would be
        // comparing two arbitrary elements. What the fixture actually needs to guarantee is that
        // the shapes are not all identical, which is an order-free property of the SET.
        final Set<Integer> methodCounts = new HashSet<>();
        for (final File classFile : generated) {
            methodCounts.add(methodCountOf(classFile));
        }
        assertTrue(methodCounts.size() > 1,
            "the rules should generate differently-shaped classes, but every one produced the "
                + "same method count: " + methodCounts);
    }

    private List<File> compileAll() throws Exception {
        final File dir = new File(outputDir, "run-" + dsls.size());
        if (dir.isDirectory()) {
            deleteRecursively(dir);
        }
        assertTrue(dir.mkdirs() || dir.isDirectory(), "could not create " + dir);

        for (int i = 0; i < dsls.size(); i++) {
            final LALClassGenerator generator = new LALClassGenerator();
            generator.setClassOutputDir(dir);
            generator.setYamlSource("source-attribution.yaml:" + (i + 1));
            generator.setClassNameHint("attribution_" + i);
            generator.compile(dsls.get(i));
        }
        final File[] classes = dir.listFiles(
            (d, name) -> name.endsWith(".class") && !name.contains("$"));
        final List<File> out = new ArrayList<>();
        if (classes != null) {
            for (final File c : classes) {
                out.add(c);
            }
        }
        return out;
    }

    private static void deleteRecursively(final File dir) {
        final File[] children = dir.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        dir.delete();
    }

    private static String sourceFileOf(final File classFile) throws Exception {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
            return new ClassFile(in).getSourceFile();
        }
    }

    private static int methodCountOf(final File classFile) throws Exception {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
            return new ClassFile(in).getMethods().size();
        }
    }

    private static List<int[]> lineNumberTableOf(final File classFile) throws Exception {
        final List<int[]> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
            final ClassFile cf = new ClassFile(in);
            for (final MethodInfo mi : cf.getMethods()) {
                final CodeAttribute code = mi.getCodeAttribute();
                if (code == null) {
                    continue;
                }
                final LineNumberAttribute lna =
                    (LineNumberAttribute) code.getAttribute(LineNumberAttribute.tag);
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
