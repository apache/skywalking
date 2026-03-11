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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code def} local variable support with
 * {@code toJson()}/{@code toJsonArray()}.
 *
 * <p>Focuses on JSON body parsing → def var → typed method chain.
 * ALS-specific proto chain tests belong in the ALS receiver module.
 */
class LALClassGeneratorDefTest extends LALClassGeneratorTestBase {

    // ==================== Source generation ====================

    @Test
    void generateSourceDefWithToJson() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    tag 'env': config?.get(\"env\")?.getAsString()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        assertTrue(source.contains("com.google.gson.JsonObject _def_config"),
            "Expected JsonObject declaration but got:\n" + source);
        assertTrue(source.contains("h.toJsonObject("),
            "Expected h.toJsonObject() call but got:\n" + source);
        assertTrue(source.contains(".get(\"env\")"),
            "Expected .get(\"env\") call but got:\n" + source);
        assertTrue(source.contains(".getAsString()"),
            "Expected .getAsString() call but got:\n" + source);
    }

    @Test
    void generateSourceDefWithToJsonArray() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def items = toJsonArray(parsed.tags)\n"
                + "    tag 'first': items?.get(0)?.getAsString()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        assertTrue(source.contains("com.google.gson.JsonArray _def_items"),
            "Expected JsonArray declaration but got:\n" + source);
        assertTrue(source.contains("h.toJsonArray("),
            "Expected h.toJsonArray() call but got:\n" + source);
        assertTrue(source.contains(".get(0)"),
            "Expected .get(0) call but got:\n" + source);
    }

    @Test
    void generateSourceDefWithCondition() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    if (config?.has(\"env\")) {\n"
                + "      tag 'env': config?.get(\"env\")?.getAsString()\n"
                + "    }\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        assertTrue(source.contains("_def_config == null") || source.contains("_def_config != null"),
            "Expected null check on _def_config but got:\n" + source);
        assertTrue(source.contains(".has(\"env\")"),
            "Expected .has(\"env\") call but got:\n" + source);
    }

    // ==================== Compilation with class output ====================

    @Test
    void compileDefWithNestedJsonAccess() throws Exception {
        compileAndAssert(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    tag 'env': config?.get(\"env\")?.getAsString()\n"
                + "    tag 'region': config?.getAsJsonObject(\"location\")"
                + "?.get(\"region\")?.getAsString()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }

    @Test
    void compileDefWithToJsonArrayAndIndex() throws Exception {
        compileAndAssert(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def items = toJsonArray(parsed.tags)\n"
                + "    if (items?.size() > 0) {\n"
                + "      tag 'first': items?.get(0)?.getAsString()\n"
                + "    }\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }

    @Test
    void compileMultipleDefs() throws Exception {
        compileAndAssert(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    def roles = toJsonArray(parsed.roles)\n"
                + "    tag 'env': config?.get(\"env\")?.getAsString()\n"
                + "    if (roles?.size() > 0) {\n"
                + "      tag 'role': roles?.get(0)?.getAsString()\n"
                + "    }\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }

    @Test
    void compileDefWithConditionGuard() throws Exception {
        compileAndAssert(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    if (config?.has(\"payload\")) {\n"
                + "      tag 'name': config?.getAsJsonObject(\"payload\")"
                + "?.get(\"name\")?.getAsString()\n"
                + "      tag 'iss': config?.getAsJsonObject(\"payload\")"
                + "?.get(\"iss\")?.getAsString()\n"
                + "    }\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }

    // ==================== Type cast on def ====================

    @Test
    void generateSourceDefWithStringCast() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def svc = parsed.service as String\n"
                + "    tag 'svc': svc\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        assertTrue(source.contains("java.lang.String _def_svc"),
            "Expected String declaration but got:\n" + source);
        assertTrue(source.contains("(java.lang.String)"),
            "Expected String cast but got:\n" + source);
    }

    @Test
    void compileDefWithQualifiedNameCast() throws Exception {
        // With inputType, parsed?.commonProperties returns AccessLogCommon.
        // The cast narrows parsed?.metadata to Metadata (which is already the
        // correct inferred type here, but demonstrates FQCN cast syntax).
        // A more realistic use: narrowing an Object-typed return to a known
        // subclass when the compiler can't infer the concrete type.
        generator.setInputType(
            io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry.class);
        compileAndAssert(
            "filter {\n"
                + "  extractor {\n"
                + "    def common = parsed?.commonProperties"
                + " as io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon\n"
                + "    tag 'cluster': common?.upstreamCluster\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }

    @Test
    void generateSourceDefWithQualifiedNameCast() {
        generator.setInputType(
            io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry.class);
        final String source = generator.generateSource(
            "filter {\n"
                + "  extractor {\n"
                + "    def common = parsed?.commonProperties"
                + " as io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon\n"
                + "    tag 'cluster': common?.upstreamCluster\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        assertTrue(source.contains(
                "io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon _def_common"),
            "Expected AccessLogCommon declaration but got:\n" + source);
        assertTrue(source.contains(
                "(io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon)"),
            "Expected AccessLogCommon cast but got:\n" + source);
    }

    // ==================== Def variable as method argument ====================

    @Test
    void generateSourceDefVarAsMethodArg() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def key = parsed.fieldName as String\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    tag 'val': config?.get(key)?.getAsString()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        // _def_key = key (String), _def_config = config (JsonObject)
        // config?.get(key) should generate _def_config.get(_def_key), not _def_config.get(null)
        assertTrue(source.contains(".get(_def_key)"),
            "Expected .get(_def_key) for def var arg but got:\n" + source);
    }

    @Test
    void generateSourceBoolLiteralAsMethodArg() {
        final String source = generator.generateSource(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    tag 'val': config?.get(\"key\")?.getAsBoolean()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        // getAsBoolean() has no args, just verify it compiles
        assertTrue(source.contains(".getAsBoolean()"),
            "Expected .getAsBoolean() call but got:\n" + source);
    }

    @Test
    void compileDefVarAsMethodArg() throws Exception {
        compileAndAssert(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    def key = parsed.fieldName as String\n"
                + "    def config = toJson(parsed.metadata)\n"
                + "    tag 'val': config?.get(key)?.getAsString()\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
    }
}
