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

package org.apache.skywalking.oap.server.testing.dsl;

import java.io.File;

/**
 * Utilities for managing generated {@code .class} file output directories.
 *
 * <p>Two patterns are supported:
 * <ul>
 *   <li><b>Unit tests</b>: {@code target/{dslType}-generated-classes/} — all classes for a DSL
 *       type go into a single directory under Maven's target dir.</li>
 *   <li><b>Checker tests</b>: {@code {sourceFile.parent}/{baseName}.generated-classes/} —
 *       classes are placed alongside their source YAML files.</li>
 * </ul>
 *
 * <p>The generated directories are git-ignored and cleaned by {@code maven-clean-plugin}.
 */
public final class DslClassOutput {

    private DslClassOutput() {
    }

    /**
     * Returns the class output directory for unit tests.
     * Output goes to {@code target/{dslType}-generated-classes/}.
     *
     * @param dslType the DSL type (e.g., {@code "lal"}, {@code "mal"}, {@code "hierarchy"})
     * @return the output directory (not created — the generator creates it on first write)
     */
    public static File unitTestDir(final String dslType) {
        return new File("target/" + dslType + "-generated-classes");
    }

    /**
     * Returns the class output directory for checker/comparison tests.
     * Output goes to {@code {sourceDir}/{baseName}.generated-classes/}.
     *
     * @param sourceFile the YAML source file
     * @return the output directory alongside the source file
     */
    public static File checkerTestDir(final File sourceFile) {
        final String baseName = sourceFile.getName()
            .replaceFirst("\\.(yaml|yml)$", "");
        return new File(sourceFile.getParent(),
            baseName + ".generated-classes");
    }
}
