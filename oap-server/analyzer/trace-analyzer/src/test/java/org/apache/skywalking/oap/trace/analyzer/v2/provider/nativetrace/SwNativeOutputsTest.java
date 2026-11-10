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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALMetricShapes;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TALTestModules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A native rule makes both kinds of output from its build blocks: a source of the existing OAL system and a new metric,
 * through the native runner as a segment arrives.
 */
class SwNativeOutputsTest {
    private static final long START = 1_700_000_000_000L;

    private final List<ISource> sources = new ArrayList<>();
    private final List<AcceptableValue<?>> metrics = new ArrayList<>();
    private ModuleManager moduleManager;
    private MeterSystem meterSystem;
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;

    @BeforeAll
    static void declareScopes() {
        if (DefaultScopeDefine.classOf("ServiceMeta") == null) {
            new DefaultScopeDefine.Listener().notify(ServiceMeta.class);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        meterSystem = spy(new MeterSystem(moduleManager));
        final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
        doNothing().when(processor).create(any(), (StreamDefinition) any(), any(), any());
        doAnswer(invocation -> metrics.add(invocation.getArgument(0))).when(meterSystem).doStreamingCalculation(any());
        final NamingControl namingControl = new NamingControl(70, 70, 150, new EndpointNameGrouping());
        MeterEntity.setNamingControl(namingControl);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> sources.add(invocation.getArgument(0))).when(sourceReceiver).receive(any());
        TALTestModules.stubServices(moduleManager, coreProvider, meterSystem, namingControl, sourceReceiver);

        final SearchableTracesTagsWatcher searchable = mock(SearchableTracesTagsWatcher.class);
        when(searchable.getSearchableTags()).thenReturn(Set.of());
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableTracesTags()).thenReturn(searchable);
        when(coreProvider.getService(ConfigService.class)).thenReturn(configService);
        final ModuleServiceHolder analyzerProvider = mock(ModuleServiceHolder.class);
        when(analyzerProvider.getService(NativeTraceAnalysisConfig.class))
            .thenReturn(mock(NativeTraceAnalysisConfig.class));
        final ModuleProviderHolder analyzerModule = mock(ModuleProviderHolder.class);
        when(analyzerModule.provider()).thenReturn(analyzerProvider);
        when(moduleManager.find(AnalyzerModule.NAME)).thenReturn(analyzerModule);
        when(moduleManager.has(AnalyzerModule.NAME)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        mockedProcessor.close();
    }

    @Test
    void aBuildBlockSendsASourceAndAMetric() throws Exception {
        final TALFunctionRegistry functions = new TALFunctionRegistry(List.of());
        functions.bind(moduleManager, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        final String dsl = "filter {\n"
            + "  if (span.spanType == \"Entry\") {\n"
            + "    extractor {\n"
            + "      build {\n"
            + "        source ServiceMeta {\n"
            + "          name formatServiceName(segment().service)\n"
            + "          layer \"GENERAL\"\n"
            + "          timeBucket minuteTimeBucket(span.startTime)\n"
            + "        }\n"
            + "        metrics { name \"native_entry_calls\"; scope service(segment().service, \"GENERAL\"); "
            + "function sum; value 1 }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
        final TalDSL rule = TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, SpanObject.class,
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE, functions, "native_outputs", null,
            DslSourceRef.ofRule("tal/test-native-outputs.yaml", 1), new TALMetricShapes(), null, null);
        final SwNativeRuleRunner runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        runner.loadRules(List.of(rule));

        runner.send(SegmentObject.newBuilder().setService("svc").setServiceInstance("inst").setTraceId("t1")
            .setTraceSegmentId("seg1")
            .addSpans(SpanObject.newBuilder().setSpanId(0).setParentSpanId(-1).setSpanType(SpanType.Entry)
                                .setOperationName("op").setStartTime(START).setEndTime(START + 10))
            .addSpans(SpanObject.newBuilder().setSpanId(1).setParentSpanId(0).setSpanType(SpanType.Exit)
                                .setOperationName("call").setStartTime(START + 1).setEndTime(START + 5))
            .build());

        assertEquals(1, sources.size(), "one source, from the entry span's block");
        assertEquals("svc", ((ServiceMeta) sources.get(0)).getName());
        assertEquals(1, metrics.size(), "one metric value, from the same block");
        final Meter meter = (Meter) metrics.get(0);
        assertEquals(IDManager.ServiceID.buildId("svc", true), meter.getEntityId());
        // The block's default timestamp is the start of the span whose run queued it.
        assertEquals(TimeBucket.getMinuteTimeBucket(START), meter.getTimeBucket());
    }

    @Test
    void aRegistryBoundWithoutModulesReadsTheServicesThroughTheRun() throws Exception {
        // The default registry a unit test compiles against binds without a module manager, so the input's functions
        // hold no service and read it through the run's FilterSpec instead.
        final NativeTraceAnalysisConfig config = moduleManager.find(AnalyzerModule.NAME).provider()
                                                              .getService(NativeTraceAnalysisConfig.class);
        when(config.isTraceAnalysis()).thenReturn(true);
        final TALFunctionRegistry functions = new TALFunctionRegistry(List.of());
        functions.bind(null, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        final String dsl = "filter {\n"
            + "  if (!traceAnalysis() || span.spanType != \"Entry\") {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    build {\n"
            + "      source ServiceMeta {\n"
            + "        name formatServiceName(segment().service)\n"
            + "        layer \"GENERAL\"\n"
            + "        timeBucket minuteTimeBucket(span.startTime)\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
        final TalDSL rule = TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, SpanObject.class,
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE, functions, "native_unbound", null,
            DslSourceRef.ofRule("tal/test-native-unbound.yaml", 1), new TALMetricShapes(), null, null);
        final SwNativeRuleRunner runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        runner.loadRules(List.of(rule));

        runner.send(SegmentObject.newBuilder().setService("svc").setServiceInstance("inst").setTraceId("t1")
            .setTraceSegmentId("seg1")
            .addSpans(SpanObject.newBuilder().setSpanId(0).setParentSpanId(-1).setSpanType(SpanType.Entry)
                                .setOperationName("op").setStartTime(START).setEndTime(START + 10))
            .build());

        assertEquals(1, sources.size(), "traceAnalysis() read the run's setting, true, so the entry span built");
    }
}
