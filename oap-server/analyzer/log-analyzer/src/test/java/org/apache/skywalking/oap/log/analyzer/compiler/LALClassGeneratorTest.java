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

package org.apache.skywalking.oap.log.analyzer.compiler;

import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.dsl.LalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LALClassGeneratorTest {

    private LALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new LALClassGenerator(new ClassPool(true));
    }

    @Test
    void compileMinimalFilter() throws Exception {
        final LalExpression expr = generator.compile(
            "filter { sink {} }");
        assertNotNull(expr);
    }

    @Test
    void compileJsonParserFilter() throws Exception {
        final LalExpression expr = generator.compile(
            "filter { json {} sink {} }");
        assertNotNull(expr);
    }

    @Test
    void compileJsonWithExtractor() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed.service as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileTextWithRegexp() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  text {\n"
            + "    regexp '(?<timestamp>\\\\d+) (?<level>\\\\w+) (?<msg>.*)'\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSinkWithEnforcer() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    enforcer {}\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void generateSourceReturnsJavaCode() {
        final String source = generator.generateSource(
            "filter { json {} sink {} }");
        assertNotNull(source);
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("filterSpec.json()"));
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("filterSpec.sink()"));
    }
}
