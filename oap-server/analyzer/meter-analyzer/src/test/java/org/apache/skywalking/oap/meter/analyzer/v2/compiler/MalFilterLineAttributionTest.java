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
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves the FILTER class's {@code LineNumberTable} against the generated source file that was
 * actually written — the one artifact this PR left unpinned.
 *
 * <p>A file-level {@code filter:} compiles to its own class, and its line geometry was a bare
 * constant (9) counted by hand off {@code wrapMalFilterSource}'s envelope. Nothing read the
 * artifact back, so editing that envelope silently shifted every filter frame by however many
 * lines were added. The constant is gone — the signature line is searched for now — and this is
 * what keeps that honest.
 */
class MalFilterLineAttributionTest {

    @TempDir
    File outputDir;

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        DSLDebugCodegenSwitch.resetInjection();
        generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
    }

    private File compileFilterAndFind(final String suffix) throws Exception {
        generator.setYamlSource("meter-analyzer-config/gateway-service.yaml:20");
        generator.setClassNameHint("filter");
        generator.compileFilter("{ tags -> tags.job_name == 'gateway' }");

        final File[] matches = outputDir.listFiles(
            (dir, name) -> name.endsWith(suffix) && !name.contains("$"));
        assertNotNull(matches, "nothing written to " + outputDir);
        assertEquals(1, matches.length, "expected exactly one generated filter " + suffix);
        return matches[0];
    }

    @Test
    void theFilterClassNamesItsRuleFileAndTheFileLevelFilterLine() throws Exception {
        final File classFile = compileFilterAndFind(".class");

        // The filter's line is the `filter:` key's own, distinct from any rule's in the document.
        assertTrue(classFile.getName().contains("_L20_"),
            "the filter's own line must reach the class name; got " + classFile.getName());
        assertTrue(classFile.getName().startsWith("meter_analyzer_config_gateway_service_"),
            "the stem must carry the catalog: MAL catalogs share one package; got "
                + classFile.getName());
    }

    @Test
    void everyFilterLineNumberEntryResolvesInsideTheGeneratedSourceFile() throws Exception {
        final File classFile = compileFilterAndFind(".class");
        final File javaFile = new File(
            classFile.getParentFile(), classFile.getName().replace(".class", ".java"));
        assertTrue(javaFile.isFile(), "no generated source file beside " + classFile.getName());

        final List<String> lines =
            Files.readAllLines(javaFile.toPath(), StandardCharsets.UTF_8);
        final List<int[]> table = MalClassAttributes.lineNumberTableOf(classFile);

        assertFalse(table.isEmpty(),
            "the filter carries no LineNumberTable at all — a lost table is silent, because "
                + "addLineNumberTable simply returns when the signature cannot be located");
        for (final int[] entry : table) {
            final int line = entry[1];
            assertTrue(line >= 1 && line <= lines.size(),
                "entry points outside the generated source file: " + line
                    + " (file has " + lines.size() + " lines)");
        }
    }

    @Test
    void everyEntryLandsInsideTheFilterMethodBody() throws Exception {
        final File classFile = compileFilterAndFind(".class");
        final File javaFile = new File(
            classFile.getParentFile(), classFile.getName().replace(".class", ".java"));

        final List<String> lines =
            Files.readAllLines(javaFile.toPath(), StandardCharsets.UTF_8);
        final List<int[]> table = MalClassAttributes.lineNumberTableOf(classFile);
        assertFalse(table.isEmpty(), "no LineNumberTable on the filter class");

        // Statement lines are counted FORWARD from the signature, so a wrong signature shifts
        // every entry by the same offset — invisible to a bounds check, which is all the class
        // had. Locating the declaration here and requiring the entries to sit between it and the
        // method's closing brace is what pins the base.
        int declaration = -1;
        for (int i = 0; i < lines.size(); i++) {
            final String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("public ") && trimmed.contains("test(")) {
                declaration = i + 1;
                break;
            }
        }
        assertTrue(declaration > 0, "no test() declaration in the generated source file");

        int closing = lines.size();
        for (int i = declaration; i < lines.size(); i++) {
            if ("}".equals(lines.get(i).trim())) {
                closing = i + 1;
                break;
            }
        }

        for (final int[] entry : table) {
            final int line = entry[1];
            assertTrue(line > declaration && line <= closing,
                "entry at line " + line + " is outside test()'s body (declared at " + declaration
                    + ", closes at " + closing + "): '" + lines.get(line - 1).trim() + "'");
        }
    }
}
