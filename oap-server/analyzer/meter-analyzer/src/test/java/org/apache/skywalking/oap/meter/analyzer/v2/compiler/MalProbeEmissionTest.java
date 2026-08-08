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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javassist.ClassPool;
import org.apache.skywalking.oap.server.core.dsldebug.DSLDebugCodegenSwitch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the codegen with debug injection ENABLED and asserts the YAML line actually reaches the
 * emitted probe call.
 *
 * <p>Every other test of the operator coordinate stops at {@code MalSourceMap.yamlLineOf(...)} —
 * they check the map, not what codegen does with it. The probe-emission path only executes when
 * injection is on, so without this test that code would ship having never run: it would compile,
 * and the map would be correct, and the emitted literal could still be wrong.
 *
 * <p>Asserting on the generated source rather than on captured records keeps the test inside this
 * module — the recorder that turns these arguments into a debug record lives in
 * {@code dsl-debugging}, downstream of here.
 */
class MalProbeEmissionTest {

    private static final int EXP_LINE = 38;
    private static final int SUFFIX_LINE = 32;

    @TempDir
    File outputDir;

    private MALClassGenerator generator;

    @BeforeEach
    void enableInjection() {
        DSLDebugCodegenSwitch.enableInjection();
        generator = new MALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(outputDir);
    }

    @AfterEach
    void resetInjection() {
        // JVM-wide switch — leaking it on would change every sibling test's generated shape.
        DSLDebugCodegenSwitch.resetInjection();
    }

    @Test
    void everyEmittedProbeCarriesAResolvedLine() throws Exception {
        final String generated = compileWithSourceMap();

        final List<int[]> probes = probeLines(generated);
        assertTrue(probes.size() >= 2,
            "expected several probe call sites with injection on, got " + probes.size());
        for (final int[] probe : probes) {
            assertTrue(probe[0] > 0,
                "a probe was emitted with a non-positive line: " + probe[0]);
        }
    }

    @Test
    void stagesFromExpAndFromExpSuffixEmitTheirOwnLines() throws Exception {
        final String generated = compileWithSourceMap();

        // The decisive assertion of the whole operator coordinate: within ONE generated method,
        // stages authored in `exp:` and the stage contributed by the file-level `expSuffix:`
        // must emit DIFFERENT lines. An implementation that resolved per-rule instead of
        // per-stage would emit EXP_LINE everywhere and still look plausible.
        assertTrue(generated.contains(", " + EXP_LINE + ");"),
            "no probe reported the exp line " + EXP_LINE + " in:\n" + generated);
        assertTrue(generated.contains(", " + SUFFIX_LINE + ");"),
            "no probe reported the expSuffix line " + SUFFIX_LINE + " in:\n" + generated);
    }

    @Test
    void withoutASourceMapProbesReportUnresolvedRatherThanAWrongLine() throws Exception {
        generator.setClassNameHint("no_map");
        generator.setYamlSource("vm.yaml:" + EXP_LINE);
        // Deliberately no setSourceMap: simulates a caller that could not resolve lines.
        generator.compile("meter_vm_no_map", "node_cpu.sum(['host']).rate('PT1M')");

        final String generated = readGenerated("no_map");
        for (final int[] probe : probeLines(generated)) {
            assertEquals(MalSourceRef.UNRESOLVED, probe[0],
                "an unmapped probe must report -1, not a plausible-looking line");
        }
    }

    /** Compiles a rule whose expSuffix is file-level, mirroring MetricConvert's formatExp. */
    private String compileWithSourceMap() throws Exception {
        final String exp = "node_cpu.sum(['host']).rate('PT1M')";
        final String suffix = "service(['host'], Layer.GENERAL)";
        final MALScriptParser.PrefixInjection injection =
            MALScriptParser.injectExpPrefixTracked(exp, null);

        generator.setClassNameHint("cpu_total");
        generator.setYamlSource("vm.yaml:" + EXP_LINE);
        generator.setSourceMap(MalSourceMap.of(injection, true, EXP_LINE, 0, SUFFIX_LINE));
        generator.compile("meter_vm_cpu_total", String.format("(%s).%s", exp, suffix));

        return readGenerated("cpu_total");
    }

    private String readGenerated(final String hint) throws Exception {
        final File[] matches = outputDir.listFiles(
            (dir, name) -> name.endsWith(".java") && name.contains(hint));
        assertNotNull(matches, "nothing written to " + outputDir);
        assertTrue(matches.length > 0, "no .java written for " + hint);
        return new String(Files.readAllBytes(matches[0].toPath()), StandardCharsets.UTF_8);
    }

    /** The trailing int literal of every {@code MALDebug.captureXxx(...)} call site. */
    private static List<int[]> probeLines(final String generated) {
        final Matcher m = Pattern.compile("MALDebug\\.capture\\w+\\([^;]*?,\\s*(-?\\d+)\\);")
                                 .matcher(generated);
        final List<int[]> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(new int[]{Integer.parseInt(m.group(1))});
        }
        return out;
    }
}
