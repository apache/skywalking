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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.io.File;
import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared base for LAL class generator tests.
 *
 * <p>Provides a fresh {@link LALClassGenerator} per test and dumps generated
 * {@code .class} files to {@code target/lal-generated-classes/} for
 * inspection with tools like {@code javap -c}.
 *
 * <p>Generated class names follow the pattern
 * {@code {TestClassName}_{testMethodName}} for easy identification,
 * e.g. {@code BasicTest_compileMinimalFilter}.
 */
abstract class LALClassGeneratorTestBase {

    private static final File CLASS_OUTPUT_DIR =
        new File("target/lal-generated-classes");

    protected LALClassGenerator generator;

    @BeforeEach
    void setUp(final TestInfo testInfo) {
        generator = new LALClassGenerator(new ClassPool(true));
        generator.setClassOutputDir(CLASS_OUTPUT_DIR);
        // Build hint from test class + method for readable .class file names
        final String className = getClass().getSimpleName()
            .replace("LALClassGenerator", "");
        final String methodName = testInfo.getTestMethod()
            .map(m -> m.getName()).orElse("unknown");
        generator.setClassNameHint(className + "_" + methodName);
    }

    /**
     * Compiles a LAL DSL string and asserts it produces a non-null expression.
     * The generated {@code .class} file is written to
     * {@code target/lal-generated-classes/} for inspection.
     */
    protected LalExpression compileAndAssert(final String dsl) throws Exception {
        final LalExpression expr = generator.compile(dsl);
        assertNotNull(expr);
        return expr;
    }
}
