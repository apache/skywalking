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

package org.apache.skywalking.oap.server.checker.mal;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link MalInputDataGenerator} to generate .data.yaml companion files
 * for all MAL test YAML scripts. This test is idempotent — it skips files
 * that already have companions.
 */
class MalInputDataGeneratorTest {

    @Test
    void generateAllInputFiles() throws Exception {
        final MalInputDataGenerator gen = new MalInputDataGenerator();
        final Path scriptsDir = gen.findScriptsDir();
        assertTrue(scriptsDir != null && Files.isDirectory(scriptsDir),
            "Cannot find scripts/mal directory");

        final String[] dirs = {
            "test-meter-analyzer-config",
            "test-otel-rules",
            "test-envoy-metrics-rules",
            "test-log-mal-rules"
        };

        int totalGenerated = 0;
        for (final String dir : dirs) {
            final Path dirPath = scriptsDir.resolve(dir);
            if (Files.isDirectory(dirPath)) {
                final int[] counts = gen.processDirectory(dirPath);
                totalGenerated += counts[0];
            }
        }

        // Verify at least some files were generated or already existed
        final long inputFileCount = Files.walk(scriptsDir)
            .filter(p -> p.toString().endsWith(".data.yaml"))
            .count();
        assertTrue(inputFileCount > 0,
            "Expected at least one .data.yaml file to exist");
    }
}
