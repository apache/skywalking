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
 *
 */

package org.apache.skywalking.oap.server.library.util.prometheus.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.apache.skywalking.oap.server.library.util.prometheus.parser.sample.TextSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrometheusLabelEscapeTest {
    @Test
    void decodesEachSupportedEscapeExactlyOnce() {
        assertEquals("C:\\DIR\\FILE.TXT", label("C:\\\\DIR\\\\FILE.TXT"));
        assertEquals("first\nsecond", label("first\\nsecond"));
        assertEquals("say \"hello\"", label("say \\\"hello\\\""));
        assertEquals("\\n", label("\\\\n"));
        assertEquals("", label(""));
        assertEquals("plain", label("plain"));
    }

    @Test
    void preservesDecodedLabelsThroughMetricFamilyParsing() throws Exception {
        final String text = "# TYPE file_access gauge\n"
            + "file_access{path=\"C:\\\\DIR\\\\FILE.TXT\",error=\"Cannot find:\\n\\\"FILE.TXT\\\"\"} 1\n";
        final TextParser parser = new TextParser(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        final MetricFamily family = parser.parse(123L);
        assertNotNull(family);
        assertEquals(1, family.getMetrics().size());
        assertEquals("C:\\DIR\\FILE.TXT", family.getMetrics().get(0).getLabels().get("path"));
        assertEquals("Cannot find:\n\"FILE.TXT\"", family.getMetrics().get(0).getLabels().get("error"));
    }

    private static String label(final String encoded) {
        return TextSample.parse("metric{label=\"" + encoded + "\"} 1").getLabels().get("label");
    }
}
