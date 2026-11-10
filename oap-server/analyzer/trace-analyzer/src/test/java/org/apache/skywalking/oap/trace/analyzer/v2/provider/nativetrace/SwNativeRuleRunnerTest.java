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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.Segment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The native runner's lifecycle, which reproduces {@code TraceAnalyzer}'s callback order, and the
 * {@code parseSegment} work it does before any rule runs.
 */
class SwNativeRuleRunnerTest {
    private final List<ISource> sent = new ArrayList<>();
    private ModuleManager moduleManager;
    private NativeTraceAnalysisConfig analysisConfig;
    private TALFunctionRegistry functions;
    private CapturingAppender appender;

    @BeforeAll
    static void declareScopes() {
        final DefaultScopeDefine.Listener scopes = new DefaultScopeDefine.Listener();
        for (final Class<?> source : List.of(Segment.class, ServiceMeta.class)) {
            if (DefaultScopeDefine.classOf(source.getSimpleName()) == null) {
                scopes.notify(source);
            }
        }
    }

    @BeforeEach
    void setUp() {
        moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(sourceReceiver).receive(any());
        TALTestModules.stubServices(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), sourceReceiver);

        final SearchableTracesTagsWatcher searchable = mock(SearchableTracesTagsWatcher.class);
        when(searchable.getSearchableTags()).thenReturn(Set.of("http.method", "db.type"));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableTracesTags()).thenReturn(searchable);
        when(coreProvider.getService(ConfigService.class)).thenReturn(configService);

        analysisConfig = mock(NativeTraceAnalysisConfig.class);
        when(analysisConfig.isError(any())).thenAnswer(invocation -> ((SpanObject) invocation.getArgument(0))
            .getIsError());
        final ModuleServiceHolder analyzerProvider = mock(ModuleServiceHolder.class);
        when(analyzerProvider.getService(NativeTraceAnalysisConfig.class)).thenReturn(analysisConfig);
        final ModuleProviderHolder analyzerModule = mock(ModuleProviderHolder.class);
        when(analyzerModule.provider()).thenReturn(analyzerProvider);
        when(moduleManager.find(AnalyzerModule.NAME)).thenReturn(analyzerModule);
        when(moduleManager.has(AnalyzerModule.NAME)).thenReturn(true);

        functions = new TALFunctionRegistry(List.of());
        functions.bind(moduleManager, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            final Logger logger = (Logger) LogManager.getLogger(SwNativeRuleRunner.class);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void theRulesRunInTheListenersCallbackOrder() throws Exception {
        final List<LogEvent> errors = capture();
        final SwNativeRuleRunner runner = runner(
            rule("a", "filter {\n"
                + "  first {\n"
                + "    writeNetworkAlias \"A.first:\" + span.operationName\n"
                + "  }\n"
                + "  writeNetworkAlias \"A:\" + span.operationName\n"
                + "  extractor {\n"
                + "    build { source ServiceMeta { name \"A.build:\" + span.operationName timeBucket 1 } }\n"
                + "  }\n"
                + "}"),
            rule("b", "filter {\n"
                + "  writeNetworkAlias \"B:\" + span.operationName\n"
                + "  if (span.operationName == \"op1\") {\n"
                + "    extractor { build { source ServiceMeta { name \"B.withdrawn\" timeBucket 1 } } }\n"
                + "    abort {}\n"
                + "  }\n"
                + "  extractor {\n"
                + "    build { source ServiceMeta { name \"B.build:\" + span.operationName timeBucket 1 } }\n"
                + "  }\n"
                + "}"));
        // Wire order as an agent sends it, span 0 after its child; the last span's type is one no listener handles.
        runner.send(segment(
            span(1, "op1", SpanType.Exit),
            span(0, "op0", SpanType.Entry),
            span(2, "op2", SpanType.Local),
            span(3, "op3", SpanType.Local).toBuilder().setSpanTypeValue(7).build()));

        assertEquals(List.of(
            "A:op1", "B:op1",
            "A.first:op0", "A:op0", "B:op0",
            "A:op2", "B:op2",
            "A:op3", "B:op3",
            "A.build:op1", "A.build:op0", "A.build:op2", "A.build:op3",
            "B.build:op0", "B.build:op2", "B.build:op3"), names(sent));
        assertEquals(1, errors.size());
        assertEquals("span type value was unexpected, span type name: UNRECOGNIZED",
            errors.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void aSegmentWithoutSpansRunsNothing() throws Exception {
        runner(rule("a", "filter { writeNetworkAlias \"x\" }")).send(SegmentObject.newBuilder().build());
        assertTrue(sent.isEmpty());
        verify(analysisConfig, never()).shouldSample(any(), anyInt(), anyBoolean());
    }

    @Test
    void theSegmentValuesAreTheListenersParseSegmentWork() throws Exception {
        when(analysisConfig.shouldSample(any(), anyInt(), anyBoolean())).thenReturn(true);
        final SwNativeRuleRunner runner = runner(rule("segment", "filter {\n"
            + "  if (buildQueued == null) {\n"
            + "    segmentDef buildQueued = true\n"
            + "    extractor {\n"
            + "      build {\n"
            + "        if (segmentSampled()) {\n"
            + "          def errorValue = 0\n"
            + "          if (segmentIsError()) {\n"
            + "            errorValue = 1\n"
            + "          }\n"
            + "          source Segment {\n"
            + "            traceId segment().traceId\n"
            + "            startTime segmentStartTime()\n"
            + "            latency segmentLatency()\n"
            + "            isError errorValue\n"
            + "            tags segmentTags()\n"
            + "            timeBucket 1\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}"));
        final String longValue = String.join("", Collections.nCopies(Tag.TAG_LENGTH + 1, "x"));
        // The start fold restarts whenever its minimum is 0, as the listener's does: 100, then 0, then 50.
        runner.send(segment(
            span(1, "a", SpanType.Exit).toBuilder().setStartTime(100).setEndTime(400)
                                       .addTags(tag("http.method", "GET")).addTags(tag("http.method", "GET"))
                                       .addTags(tag("other", "x")).build(),
            span(2, "b", SpanType.Exit).toBuilder().setStartTime(0).setEndTime(300).setIsError(true)
                                       .addTags(tag("db.type", longValue)).build(),
            span(0, "c", SpanType.Entry).toBuilder().setStartTime(50).setEndTime(500)
                                        .addTags(tag("db.type", "Mysql")).build()));

        assertEquals(1, sent.size());
        final Segment record = (Segment) sent.get(0);
        assertEquals("t1", record.getTraceId());
        assertEquals(50, record.getStartTime());
        assertEquals(450, record.getLatency());
        assertEquals(1, record.getIsError());
        assertEquals(List.of(new Tag("http.method", "GET"), new Tag("db.type", "Mysql")), record.getTags());
        verify(analysisConfig).shouldSample(any(), eq(450), eq(true));
    }

    @Test
    void aSegmentBeforeTheRulesLoadIsRefused() {
        // Registered in prepare(), loaded in start(), which every caller of the service waits for.
        final SwNativeRuleRunner runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        assertThrows(IllegalStateException.class,
            () -> runner.send(segment(span(0, "op0", SpanType.Entry))));
    }

    @Test
    void aRuleCannotChangeTheSegmentsTags() throws Exception {
        final SwNativeRuleRunner runner = runner(rule("remover", "filter { def removed = segmentTags().remove(0) }"));
        // The runner catches nothing, so the refused change stops the segment as a throwing listener does.
        assertThrows(UnsupportedOperationException.class, () -> runner.send(segment(
            span(0, "op0", SpanType.Entry).toBuilder().addTags(tag("http.method", "GET")).build())));
    }

    // ==================== Harness ====================

    private SwNativeRuleRunner runner(final TalDSL... rules) {
        final SwNativeRuleRunner runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        runner.loadRules(List.of(rules));
        return runner;
    }

    private TalDSL rule(final String name, final String dsl) throws Exception {
        return TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, SpanObject.class,
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE, functions, "runner_" + name + "_" + Math.abs(dsl.hashCode()),
            null, DslSourceRef.ofRule("tal/test-runner.yaml", 1), new TALMetricShapes(), null, null);
    }

    private static List<String> names(final List<ISource> sources) {
        final List<String> names = new ArrayList<>();
        for (final ISource source : sources) {
            names.add(source instanceof NetworkAddressAliasSetup
                ? ((NetworkAddressAliasSetup) source).getAddress() : ((ServiceMeta) source).getName());
        }
        return names;
    }

    private static KeyStringValuePair tag(final String key, final String value) {
        return KeyStringValuePair.newBuilder().setKey(key).setValue(value).build();
    }

    private static SpanObject span(final int spanId, final String operationName, final SpanType type) {
        return SpanObject.newBuilder().setSpanId(spanId).setParentSpanId(spanId == 0 ? -1 : 0)
                         .setOperationName(operationName).setSpanType(type)
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L).build();
    }

    private static SegmentObject segment(final SpanObject... spans) {
        final SegmentObject.Builder segment = SegmentObject.newBuilder()
            .setService("svc").setServiceInstance("inst").setTraceId("t1").setTraceSegmentId("seg1");
        for (final SpanObject span : spans) {
            segment.addSpans(span);
        }
        return segment.build();
    }

    private List<LogEvent> capture() {
        // On the logger's configuration, which every Logger instance of the name reads.
        Configurator.setLevel(SwNativeRuleRunner.class.getName(), Level.ERROR);
        final Logger logger = (Logger) LogManager.getLogger(SwNativeRuleRunner.class);
        appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);
        return appender.events;
    }

    private static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        private CapturingAppender() {
            super("native-runner-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
