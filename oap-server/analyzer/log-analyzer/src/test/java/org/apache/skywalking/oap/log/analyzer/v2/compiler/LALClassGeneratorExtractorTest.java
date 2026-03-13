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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extractor feature tests: ProcessRegistry, metrics, inputType, outputType,
 * output field assignment, and LogData fallback.
 */
class LALClassGeneratorExtractorTest extends LALClassGeneratorTestBase {

    // ==================== ProcessRegistry static calls ====================

    @Test
    void compileProcessRegistryCall() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service ProcessRegistry.generateVirtualLocalProcess("
            + "parsed.service as String, parsed.serviceInstance as String"
            + ") as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    @Test
    void compileProcessRegistryWithThreeArgs() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service ProcessRegistry.generateVirtualRemoteProcess("
            + "parsed.service as String, parsed.serviceInstance as String, "
            + "parsed.address as String) as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    // ==================== Metrics block ====================

    @Test
    void compileMetricsBlock() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    metrics {\n"
            + "      timestamp log.timestamp as Long\n"
            + "      labels level: parsed.level, service: log.service\n"
            + "      name \"nginx_error_log_count\"\n"
            + "      value 1\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    // ==================== Complex production-like rules ====================

    @Test
    void compileNginxAccessLogRule() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  if (tag(\"LOG_KIND\") == \"NGINX_ACCESS_LOG\") {\n"
            + "    text {\n"
            + "      regexp '.+\"(?<request>.+)\"(?<status>\\\\d{3}).+'\n"
            + "    }\n"
            + "    extractor {\n"
            + "      if (parsed.status) {\n"
            + "        tag 'http.status_code': parsed.status\n"
            + "      }\n"
            + "    }\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
    }

    @Test
    void compileEnvoyAlsAbortRuleFailsWithoutInputType() {
        assertThrows(IllegalArgumentException.class, () -> generator.compile(
            "filter {\n"
            + "  if (parsed?.response?.responseCode?.value as Integer < 400"
            + " && !parsed?.commonProperties?.responseFlags?.toString()?.trim()) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    if (parsed?.response?.responseCode) {\n"
            + "      tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "    }\n"
            + "    tag 'response.flag': parsed?.commonProperties?.responseFlags\n"
            + "  }\n"
            + "  sink {}\n"
            + "}"));
    }

    @Test
    void compileNoParserFallsBackToLogMetadata() throws Exception {
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    service parsed.service as String\n"
            + "    instance parsed.serviceInstance as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.ctx().metadata().getService()"),
            "Expected h.ctx().metadata().getService() but got: " + source);
        assertTrue(source.contains("h.ctx().metadata().getServiceInstance()"),
            "Expected h.ctx().metadata().getServiceInstance() but got: " + source);
        assertFalse(source.contains("_p"),
            "Should NOT have _p variable for LogMetadata fallback but got: " + source);
        compileAndAssert(dsl);
    }

    @Test
    void compileInputTypeGeneratesDirectGetterCalls() throws Exception {
        generator.setInputType(
            io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry.class);
        final String dsl =
            "filter {\n"
            + "  if (parsed?.response?.responseCode?.value as Integer < 400"
            + " && !parsed?.commonProperties?.responseFlags?.toString()?.trim()) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    if (parsed?.response?.responseCode) {\n"
            + "      tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "    }\n"
            + "    tag 'response.flag': parsed?.commonProperties?.responseFlags\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        final String source = generator.generateSource(dsl);
        final String fqcn =
            "io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry";
        assertTrue(source.contains(
            fqcn + " _p = (" + fqcn + ") h.ctx().input()"),
            "Expected _p local variable for inputType cast but got: " + source);
        assertTrue(source.contains("_p.getResponse()"),
            "Expected _p.getResponse() via cached variable but got: " + source);
        assertTrue(source.contains("_p.getCommonProperties()"),
            "Expected _p.getCommonProperties() via cached variable but got: "
            + source);
        assertFalse(source.contains("getAt"),
            "Should NOT contain getAt calls but got: " + source);
        assertTrue(source.contains("_p == null ? null :"),
            "Expected null checks for ?. safe navigation but got: " + source);
        assertTrue(source.contains("_t0") && source.contains("_t1"),
            "Expected _tN local variables for chain dedup but got: " + source);
        assertTrue(source.contains(".getValue() < 400"),
            "Expected direct primitive comparison without boxing but got: "
            + source);
        assertFalse(source.contains("h.toLong"),
            "Should NOT use h.toLong for primitive int comparison but got: "
            + source);
        assertTrue(source.contains("_o.addTag(\"status.code\""),
            "Expected _o.addTag(key, value) but got: " + source);
        assertFalse(source.contains("singletonMap"),
            "Should NOT use singletonMap for single tags but got: " + source);
        compileAndAssert(dsl);
    }

    // ==================== Output field assignment ====================

    @Test
    void compileOutputFieldAssignment() throws Exception {
        generator.setOutputType(TestOutputType.class);
        final String dsl = "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    statement parsed.statement as String\n"
                + "    latency parsed.latency as Long\n"
                + "  }\n"
                + "  sink {}\n"
                + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains(".setStatement("),
            "Expected direct setStatement() call but got: " + source);
        assertTrue(source.contains(".setLatency("),
            "Expected direct setLatency() call but got: " + source);
        assertTrue(source.contains("h.toStr("),
            "Expected toStr() cast for String but got: " + source);
        assertTrue(source.contains("h.toLong("),
            "Expected toLong() cast for Long but got: " + source);
        assertTrue(source.contains("h.ctx().output()"),
            "Expected ctx.output() access but got: " + source);
        assertTrue(source.contains("h.ctx().setOutput(new"),
            "Expected output object creation but got: " + source);
    }

    @Test
    void compileOutputFieldWithOutputTypeValidation() throws Exception {
        generator.setOutputType(TestOutputType.class);
        final String dsl = "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    statement parsed.stmt as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains(".setStatement("),
            "Expected direct setStatement() call but got: " + source);
    }

    @Test
    void compileOutputFieldWithInvalidSetter() {
        generator.setOutputType(TestOutputType.class);
        final Exception ex = assertThrows(RuntimeException.class, () ->
            generator.generateSource(
                "filter {\n"
                    + "  json {}\n"
                    + "  extractor {\n"
                    + "    nonExistentField parsed.x as String\n"
                    + "  }\n"
                    + "  sink {}\n"
                    + "}"));
        assertTrue(ex.getMessage().contains("setNonExistentField"),
            "Expected error about missing setter but got: " + ex.getMessage());
    }

    /**
     * Test output type with custom fields (statement, latency).
     */
    public static class TestOutputType {
        private String statement;
        private long latency;

        public void setStatement(final String statement) {
            this.statement = statement;
        }

        public String getStatement() {
            return statement;
        }

        public void setLatency(final long latency) {
            this.latency = latency;
        }

        public long getLatency() {
            return latency;
        }
    }
}
