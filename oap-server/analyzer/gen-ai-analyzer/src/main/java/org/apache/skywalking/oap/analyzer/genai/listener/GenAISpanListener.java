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

package org.apache.skywalking.oap.analyzer.genai.listener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.analyzer.genai.config.GenAITagKeys;
import org.apache.skywalking.oap.analyzer.genai.module.GenAIAnalyzerModule;
import org.apache.skywalking.oap.analyzer.genai.service.IGenAIMeterAnalyzerService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.otel.SpanListener;
import org.apache.skywalking.oap.server.core.otel.SpanListenerResult;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * {@link SpanListener} that extracts GenAI metrics from Zipkin spans.
 *
 * <p>Implements {@link #onZipkinSpan} (phase 2) so it works for all trace sources
 * (OTLP, Zipkin HTTP, Kafka). Replicates the logic previously hardcoded in
 * {@code SpanForward.processGenAILogic()}.
 *
 * <p>Sources (GenAI metrics) are emitted directly to {@link SourceReceiver} — not
 * returned in the result. The result only carries additional tags (estimated cost).
 */
@Slf4j
public class GenAISpanListener implements SpanListener {
    private IGenAIMeterAnalyzerService analyzerService;
    private SourceReceiver sourceReceiver;

    @Override
    public void init(final ModuleManager moduleManager) {
        analyzerService = moduleManager.find(GenAIAnalyzerModule.NAME)
                                       .provider()
                                       .getService(IGenAIMeterAnalyzerService.class);
        sourceReceiver = moduleManager.find(CoreModule.NAME)
                                      .provider()
                                      .getService(SourceReceiver.class);
    }

    @Override
    public SpanListenerResult onZipkinSpan(final ZipkinSpan span) {
        final GenAIMetrics metrics = analyzerService.extractMetricsFromZipkinSpan(span);
        if (metrics == null) {
            return SpanListenerResult.CONTINUE;
        }

        // Emit GenAI sources directly
        for (final Source source : analyzerService.transferToSources(metrics)) {
            sourceReceiver.receive(source);
        }

        // Return additional tags (estimated cost) for the caller to merge
        Map<String, String> additionalTags = new HashMap<>();
        if (metrics.getTotalEstimatedCost() > 0) {
            final BigDecimal calculatedCost = BigDecimal.valueOf(metrics.getTotalEstimatedCost())
                .divide(new BigDecimal("1000000"), 10, RoundingMode.HALF_UP);
            additionalTags.put(
                GenAITagKeys.ESTIMATED_COST,
                calculatedCost.stripTrailingZeros().toPlainString());
        }

        return SpanListenerResult.builder()
                                .shouldPersist(true)
                                .additionalTags(additionalTags)
                                .build();
    }
}
