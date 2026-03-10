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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic compilation and error handling tests for LAL class generator.
 */
class LALClassGeneratorBasicTest extends LALClassGeneratorTestBase {

    // ==================== Minimal compile tests ====================

    @Test
    void compileMinimalFilter() throws Exception {
        compileAndAssert("filter { sink {} }");
    }

    @Test
    void compileJsonParserFilter() throws Exception {
        compileAndAssert("filter { json {} sink {} }");
    }

    @Test
    void compileJsonWithExtractor() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed.service as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    @Test
    void compileTextWithRegexp() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  text {\n"
            + "    regexp '(?<timestamp>\\\\d+) (?<level>\\\\w+) (?<msg>.*)'\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    @Test
    void compileSinkWithEnforcer() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    enforcer {}\n"
            + "  }\n"
            + "}");
    }

    @Test
    void generateSourceReturnsJavaCode() {
        final String source = generator.generateSource(
            "filter { json {} sink {} }");
        assertNotNull(source);
        assertTrue(source.contains("filterSpec.json(ctx)"));
        assertTrue(source.contains("filterSpec.sink(ctx)"));
    }

    // ==================== Error handling ====================

    @Test
    void emptyScriptThrows() {
        assertThrows(Exception.class, () -> generator.compile(""));
    }

    @Test
    void missingFilterKeywordThrows() {
        assertThrows(Exception.class, () -> generator.compile("json {}"));
    }

    @Test
    void unclosedBraceThrows() {
        assertThrows(Exception.class,
            () -> generator.compile("filter { json {"));
    }

    @Test
    void invalidStatementInFilterThrows() {
        assertThrows(Exception.class,
            () -> generator.compile("filter { invalid {} }"));
    }
}
