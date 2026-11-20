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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log;

import java.util.Collections;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.AutoListeners;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListenerFactory;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A log without a layer runs the auto rules, then, when none claimed it, the fallback auto rules, and is
 * dropped when those don't claim it either; nothing routes it to the GENERAL rules.
 */
class LogAnalyzerRoutingTest {

    private LogAnalysisListenerFactory factory;
    private LogAnalysisListener auto;
    private LogAnalysisListener fallback;
    private LogAnalysisListener general;
    private LogAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        factory = mock(LogAnalysisListenerFactory.class);
        auto = mock(LogAnalysisListener.class);
        fallback = mock(LogAnalysisListener.class);
        general = mock(LogAnalysisListener.class);
        when(factory.createAuto()).thenReturn(new AutoListeners(auto, fallback));
        when(factory.create(Layer.GENERAL)).thenReturn(general);
        final ILogAnalysisListenerManager manager = mock(ILogAnalysisListenerManager.class);
        when(manager.getLogAnalysisListenerFactories()).thenReturn(Collections.singletonList(factory));
        analyzer = new LogAnalyzer(manager);
    }

    private static LogMetadata log(final String layer) {
        return LogMetadata.builder().service("svc").layer(layer).build();
    }

    @Test
    void autoRuleClaimingTheLogSkipsTheFallbackRules() {
        when(auto.claimed()).thenReturn(true);

        analyzer.doAnalysis(log(""), null);

        verify(auto).build();
        verify(fallback, never()).build();
        verify(general, never()).build();
    }

    @Test
    void fallbackRulesRunWhenNoAutoRuleClaimedTheLog() {
        when(fallback.claimed()).thenReturn(true);

        analyzer.doAnalysis(log(""), null);

        verify(auto).build();
        verify(fallback).build();
        verify(general, never()).build();
    }

    @Test
    void logNoRuleClaimedIsNotRoutedToGeneral() {
        analyzer.doAnalysis(log(""), null);

        verify(fallback).build();
        verify(factory, never()).create(Layer.GENERAL);
    }

    @Test
    void logWithLayerRunsOnlyThatLayersRules() {
        analyzer.doAnalysis(log("GENERAL"), null);

        verify(general).parse(any(), any());
        verify(general).build();
        verify(factory, never()).createAuto();
    }
}
