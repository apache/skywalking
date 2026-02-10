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

package org.apache.skywalking.oal.v2.generator;

import freemarker.template.Configuration;
import freemarker.template.Version;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.rt.OALRuntime;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Service;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Diagnostic test to compare generated source code from V1 and V2.
 */
@Slf4j
public class DiagnosticTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    @BeforeAll
    public static void initializeScopes() {
        try {
            DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();
            listener.notify(Service.class);
        } catch (RuntimeException e) {
            // Scope may already be registered by other tests
        }
    }

    @Test
    public void compareGeneratedDoMetrics() throws Exception {
        String oal = "service_cpm = from(Service.*).cpm();";

        // V1
        String v1Source = generateV1DoMetrics(oal);
        log.info("=== V1 Generated doMetrics (length={}) ===\n{}", v1Source.length(), v1Source);

        // V2
        String v2Source = generateV2DoMetrics(oal);
        log.info("=== V2 Generated doMetrics (length={}) ===\n{}", v2Source.length(), v2Source);

        // Character by character comparison
        int minLen = Math.min(v1Source.length(), v2Source.length());
        for (int i = 0; i < minLen; i++) {
            if (v1Source.charAt(i) != v2Source.charAt(i)) {
                log.info("First difference at index {}", i);
                log.info("V1 char: '{}' (code: {})", v1Source.charAt(i), (int) v1Source.charAt(i));
                log.info("V2 char: '{}' (code: {})", v2Source.charAt(i), (int) v2Source.charAt(i));
                log.info("V1 context: {}", v1Source.substring(Math.max(0, i - 20), Math.min(v1Source.length(), i + 20)));
                log.info("V2 context: {}", v2Source.substring(Math.max(0, i - 20), Math.min(v2Source.length(), i + 20)));
                break;
            }
        }
        if (v1Source.length() != v2Source.length()) {
            log.info("Length difference: V1={}, V2={}", v1Source.length(), v2Source.length());
        }

        Assertions.assertEquals(v1Source, v2Source, "V1 and V2 should generate identical source code");
    }

    private String generateV1DoMetrics(String oal) throws Exception {
        ScriptParser scriptParser = ScriptParser.createFromScriptText(oal, SOURCE_PACKAGE);
        OALScripts scripts = scriptParser.parse();
        List<AnalysisResult> results = scripts.getMetricsStmts();

        AnalysisResult metricsStmt = results.get(0);
        metricsStmt.setMetricsClassPackage(METRICS_PACKAGE);
        metricsStmt.setSourcePackage(SOURCE_PACKAGE);

        Configuration configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(OALRuntime.class.getClassLoader(), "/code-templates");

        StringWriter methodEntity = new StringWriter();
        configuration.getTemplate("dispatcher/doMetrics.ftl").process(metricsStmt, methodEntity);
        return methodEntity.toString();
    }

    private String generateV2DoMetrics(String oal) throws Exception {
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        List<MetricDefinition> metrics = parser.getMetrics();

        CodeGenModel model = enricher.enrich(metrics.get(0));

        Configuration configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(OALClassGeneratorV2.class.getClassLoader(), "/code-templates-v2");

        StringWriter methodEntity = new StringWriter();
        configuration.getTemplate("dispatcher/doMetrics.ftl").process(model, methodEntity);
        return methodEntity.toString();
    }
}
