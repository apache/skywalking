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
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A generated class's {@code SourceFile} must name the {@code .java} actually written beside it.
 *
 * <p>LAL previously stamped {@code "(execution-basic.yaml:304)auto-layer-not-set.java"} — the rule's
 * YAML provenance — while writing {@code execution_basic_L304_auto_layer_not_set.java}. Those never
 * match, so IDE source-attach could not resolve a single LAL frame. It reads as correct precisely
 * because the name looks informative; the failure only shows up when someone tries to open it.
 *
 * <p>Provenance belongs in the debug API, which reports the rule's YAML location per record. The
 * bytecode attribute has exactly one job: name a file that exists.
 */
class LALSourceFileResolvesTest {

    @TempDir
    File outputDir;

    private static final String DSL =
        "filter {\n"
            + "  text {\n"
            + "    abortOnFailure false\n"
            + "  }\n"
            + "  sink {\n"
            + "  }\n"
            + "}\n";

    @Test
    void theSourceFileAttributeNamesTheSidecarThatWasActuallyWritten() throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource("execution-basic.yaml:304");
        generator.setClassNameHint("auto-layer-not-set");
        generator.compile(DSL);

        final File[] classes = outputDir.listFiles(
            (dir, name) -> name.endsWith(".class") && !name.contains("$"));
        assertNotNull(classes, "nothing written to " + outputDir);
        assertTrue(classes.length > 0, "no .class written");

        for (final File generated : classes) {
            final String sourceFile;
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(generated.toPath())))) {
                sourceFile = new ClassFile(in).getSourceFile();
            }
            // A generated source file exists only under SW_DYNAMIC_CLASS_ENGINE_DEBUG, so in production this
            // attribute cannot usefully name one. It names the RULE -- what an operator opens --
            // and appends the generated file name for anyone who did dump classes.
            assertEquals("(execution-basic.yaml:304)"
                    + generated.getName().replace(".class", ".java"),
                sourceFile,
                "SourceFile must carry the rule location plus the generated file name");
        }
    }

    @Test
    void aNestedRulePathSurvivesWhereTheClassNameCannotEncodeIt() throws Exception {
        // Sanitising maps '/', '-' and '.' all to '_', so lal_oap_cases_envoy_als could be any of
        // several paths. Only this attribute disambiguates.
        final LALClassGenerator generator = new LALClassGenerator();
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource("lal/oap-cases/envoy-als.yaml:58");
        generator.setClassNameHint("envoy-als-tcp");
        generator.compile(DSL);

        for (final File generated : outputDir.listFiles(
                (dir, name) -> name.endsWith(".class") && name.contains("envoy"))) {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(generated.toPath())))) {
                assertTrue(new ClassFile(in).getSourceFile()
                        .startsWith("(lal/oap-cases/envoy-als.yaml:58)"),
                    "the full rule path must survive into SourceFile");
            }
        }
    }

    @Test
    void theSidecarIsUtf8RegardlessOfThePlatformDefaultCharset() throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        generator.setClassOutputDir(outputDir);
        generator.setYamlSource("execution-basic.yaml:304");
        generator.setClassNameHint("auto-layer-not-set");
        generator.compile(DSL);

        final File[] sourceFiles = outputDir.listFiles((dir, name) -> name.endsWith(".java"));
        assertNotNull(sourceFiles);
        assertTrue(sourceFiles.length > 0, "no .java written");

        for (final File sourceFile : sourceFiles) {
            // Decoding as UTF-8 must not throw. A FileWriter on a JVM defaulting to GBK /
            // windows-1252 would have written bytes that are not valid UTF-8, which is exactly
            // how the equivalent MAL generated source file broke CI with MalformedInputException.
            final String content =
                new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
            assertTrue(content.contains("Synthetic source"), "header missing in " + sourceFile);
            for (int i = 0; i < content.length(); i++) {
                assertTrue(content.charAt(i) != '�',
                    "replacement char at " + i + " means the file is not valid UTF-8: " + sourceFile);
            }
        }
    }
}
