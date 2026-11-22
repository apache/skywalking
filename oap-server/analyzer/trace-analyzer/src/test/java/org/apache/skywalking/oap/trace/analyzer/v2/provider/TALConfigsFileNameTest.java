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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A listed name reads its one file, or, beside an editor's backup, its {@code .yaml} or {@code .yml} file; two rule
 * files of one name are refused rather than one replacing the other.
 */
class TALConfigsFileNameTest {
    private File dir;

    @BeforeEach
    void setUp() throws Exception {
        // A directory on the test class path, since rule files are found through the class loader.
        final File classes = new File(TALConfigsFileNameTest.class.getClassLoader()
                                                                  .getResource("tal-static-files").toURI())
            .getParentFile();
        dir = new File(classes, "tal-file-names-" + System.nanoTime());
        assertTrue(dir.mkdir(), dir.toString());
    }

    @AfterEach
    void tearDown() {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    @Test
    void twoRuleFilesOfOneNameAreRefused() throws Exception {
        write("payments.yaml", rule("current"));
        write("payments.yml", rule("other"));
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> TALConfigs.load(dir.getName(), List.of("payments"), null));
        assertTrue(refused.getMessage().contains("payments.yaml") && refused.getMessage().contains("payments.yml")
            && refused.getMessage().contains("keep one"), refused.getMessage());
    }

    @Test
    void aRuleFileThatCannotBeReadStillClaimsItsName() throws Exception {
        write("payments.yaml", rule("current"));
        write("payments.yml", rule("other"));
        final File unreadable = new File(dir, "payments.yaml");
        // Windows keeps a file readable (setReadable(false) is refused); a privileged process reads it anyway
        assumeTrue(unreadable.setReadable(false), "the platform cannot take a file's read permission away");
        assumeFalse(Files.isReadable(unreadable.toPath()), "the process reads files whatever their permissions");
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> TALConfigs.load(dir.getName(), List.of("payments"), null));
        assertTrue(refused.getMessage().contains("payments.yaml") && refused.getMessage().contains("payments.yml"),
            refused.getMessage());
    }

    @Test
    void aLoneFileLoadsWhateverItsExtensionUnderItsOwnName() throws Exception {
        write("payments.YAML", rule("current"));
        write("pay.conf", rule("conf"));
        write("orders", rule("bare"));
        final List<TALConfigs> loaded = TALConfigs.load(dir.getName(), List.of("payments", "pay", "orders"), null);
        assertEquals(Set.of("tal/payments.YAML", "tal/pay.conf", "tal/orders"), loaded.stream()
            .map(file -> file.getRules().get(0).getSourcePath()).collect(Collectors.toSet()));
    }

    @Test
    void aFileOfLayerDefinitionsAloneRegistersThem() throws Exception {
        write("layers.yaml",
            "layerDefinitions:\n  - name: TAL_LAYERS_ONLY_FILE\n    ordinal: 9961\n    normal: true\n");
        assertTrue(TALConfigs.load(dir.getName(), List.of("layers"), null).isEmpty());
        assertEquals("TAL_LAYERS_ONLY_FILE", Layer.valueOf("TAL_LAYERS_ONLY_FILE").name());
    }

    @Test
    void theYamlFileIsReadBesideABackup() throws Exception {
        // `orders.yaml~` is an editor's backup, not a second file of the rule.
        write("orders.yaml", rule("fresh"));
        write("orders.yaml~", "requiredModules:\n  - backup-only\n" + rule("stale"));
        final List<TALConfigs> loaded = TALConfigs.load(dir.getName(), List.of("orders"), null);
        assertEquals(List.of("fresh"), loaded.stream().flatMap(file -> file.getRules().stream())
                                             .map(TALConfig::getName).collect(Collectors.toList()));
        assertTrue(TALConfigs.requiredModulesOf(dir.getName(), List.of("orders")).isEmpty());
    }

    private static String rule(final String name) {
        return "rules:\n  - name: " + name + "\n    layer: GENERAL\n    dsl: |\n      filter { abort {} }\n";
    }

    private void write(final String name, final String content) throws Exception {
        Files.write(new File(dir, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
