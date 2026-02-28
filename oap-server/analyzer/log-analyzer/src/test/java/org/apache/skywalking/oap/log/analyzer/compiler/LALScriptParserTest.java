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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LALScriptParserTest {

    @Test
    void parseMinimalFilter() {
        final LALScriptModel model = LALScriptParser.parse("filter { sink {} }");
        assertNotNull(model);
        assertEquals(1, model.getStatements().size());
        assertInstanceOf(LALScriptModel.SinkBlock.class, model.getStatements().get(0));
    }

    @Test
    void parseJsonParserWithExtractorAndSink() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    service parsed.service as String\n"
                + "    layer parsed.layer as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        assertEquals(3, model.getStatements().size());
        assertInstanceOf(LALScriptModel.JsonParser.class, model.getStatements().get(0));

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);
        assertEquals(2, extractor.getStatements().size());

        final LALScriptModel.FieldAssignment serviceField =
            (LALScriptModel.FieldAssignment) extractor.getStatements().get(0);
        assertEquals(LALScriptModel.FieldType.SERVICE, serviceField.getFieldType());
        assertTrue(serviceField.getValue().isParsedRef());
        assertEquals("String", serviceField.getCastType());
    }

    @Test
    void parseTextParserWithRegexp() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  text {\n"
                + "    regexp '.+\"(?<request>.+)\"(?<status>\\d{3}).+'\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        assertEquals(2, model.getStatements().size());
        final LALScriptModel.TextParser textParser =
            (LALScriptModel.TextParser) model.getStatements().get(0);
        assertNotNull(textParser.getRegexpPattern());
    }

    @Test
    void parseSlowSql() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    layer parsed.layer as String\n"
                + "    service parsed.service as String\n"
                + "    timestamp parsed.time as String\n"
                + "    slowSql {\n"
                + "      id parsed.id as String\n"
                + "      statement parsed.statement as String\n"
                + "      latency parsed.query_time as Long\n"
                + "    }\n"
                + "  }\n"
                + "}");

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);

        // Find the slowSql block
        LALScriptModel.SlowSqlBlock slowSql = null;
        for (final LALScriptModel.ExtractorStatement stmt : extractor.getStatements()) {
            if (stmt instanceof LALScriptModel.SlowSqlBlock) {
                slowSql = (LALScriptModel.SlowSqlBlock) stmt;
            }
        }
        assertNotNull(slowSql);
        assertNotNull(slowSql.getId());
        assertEquals("String", slowSql.getIdCast());
        assertNotNull(slowSql.getStatement());
        assertEquals("String", slowSql.getStatementCast());
        assertNotNull(slowSql.getLatency());
        assertEquals("Long", slowSql.getLatencyCast());
    }

    @Test
    void parseMetricsBlock() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
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

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(0);
        final LALScriptModel.MetricsBlock metrics =
            (LALScriptModel.MetricsBlock) extractor.getStatements().get(0);

        assertEquals("nginx_error_log_count", metrics.getName());
        assertEquals(2, metrics.getLabels().size());
        assertTrue(metrics.getLabels().containsKey("level"));
        assertTrue(metrics.getLabels().containsKey("service"));
        assertNotNull(metrics.getValue());
    }

    @Test
    void parseSinkWithSampler() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  sink {\n"
                + "    sampler {\n"
                + "      rateLimit('service:error') {\n"
                + "        rpm 6000\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");

        final LALScriptModel.SinkBlock sink =
            (LALScriptModel.SinkBlock) model.getStatements().get(0);
        assertEquals(1, sink.getStatements().size());
        final LALScriptModel.SamplerBlock sampler =
            (LALScriptModel.SamplerBlock) sink.getStatements().get(0);
        assertEquals(1, sampler.getContents().size());
        final LALScriptModel.RateLimitBlock rateLimit =
            (LALScriptModel.RateLimitBlock) sampler.getContents().get(0);
        assertEquals("service:error", rateLimit.getId());
        assertEquals(6000, rateLimit.getRpm());
    }

    @Test
    void parseIfCondition() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  if (parsed.status) {\n"
                + "    extractor {\n"
                + "      layer parsed.layer as String\n"
                + "    }\n"
                + "    sink {}\n"
                + "  }\n"
                + "}");

        assertEquals(1, model.getStatements().size());
        final LALScriptModel.IfBlock ifBlock =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        assertNotNull(ifBlock.getCondition());
        assertEquals(2, ifBlock.getThenBranch().size());
    }

    @Test
    void parseSyntaxErrorThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LALScriptParser.parse("filter {"));
    }
}
