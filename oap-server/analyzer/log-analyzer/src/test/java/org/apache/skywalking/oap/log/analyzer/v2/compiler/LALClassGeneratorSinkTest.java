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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sampler, rateLimit, and interpolated ID tests.
 */
class LALClassGeneratorSinkTest extends LALClassGeneratorTestBase {

    @Test
    void compileSamplerWithRateLimit() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit('service:error') {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
    }

    @Test
    void compileSamplerWithInterpolatedId() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit(\"${log.service}:${parsed.code}\") {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
    }

    @Test
    void parseInterpolatedIdParts() {
        final java.util.List<LALScriptModel.InterpolationPart> parts =
            LALScriptParser.parseInterpolation(
                "${log.service}:${parsed.code}");
        assertNotNull(parts);
        assertEquals(3, parts.size());
        assertFalse(parts.get(0).isLiteral());
        assertTrue(parts.get(0).getExpression().isLogRef());
        assertTrue(parts.get(1).isLiteral());
        assertEquals(":", parts.get(1).getLiteral());
        assertFalse(parts.get(2).isLiteral());
        assertTrue(parts.get(2).getExpression().isParsedRef());
    }

    @Test
    void compileSamplerWithSafeNavInterpolatedId() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit(\"${log.service}:${parsed?.commonProperties"
            + "?.responseFlags?.toString()}\") {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
    }

    @Test
    void compileSamplerWithIfAndRateLimit() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      if (parsed?.error) {\n"
            + "        rateLimit('svc:err') {\n"
            + "          rpm 6000\n"
            + "        }\n"
            + "      } else {\n"
            + "        rateLimit('svc:ok') {\n"
            + "          rpm 3000\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
    }
}
