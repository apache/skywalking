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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListenerFactory;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The layer a rule saves a log under, as FilterSpec#doSink checks it. An auto rule saves a log only under a layer it
 * decided itself, read from its own decision, not from its {@link LogMetadata}: these tests give two rules one
 * metadata, which LogFilterListener no longer does (each rule gets its own copy), so the check holds even if a rule's
 * metadata held a layer it did not decide. And any rule saves a log only under a registered layer, which the sink
 * listeners turn into a {@code Layer}.
 */
class LALSinkLayerTest {
    private static final String SETS_LAYER = "filter { extractor { layer \"IOS\" } sink {} }";
    private static final String SETS_NO_LAYER = "filter { extractor { } sink {} }";
    /**
     * A computed layer: the body's {@code kind}, null when the body has none.
     */
    private static final String KIND_LAYER = "filter { json {} extractor { layer parsed.kind as String } sink {} }";

    /**
     * The layer each saved log was saved under, in order.
     */
    private final List<String> saved = new ArrayList<>();
    private FilterSpec filterSpec;
    private LogMetadata metadata;

    @BeforeEach
    void setUp() throws Exception {
        filterSpec = filterSpec();
        metadata = LogMetadata.builder().service("svc").layer("").timestamp(1L).build();
    }

    @Test
    void aRuleThatDecidedALayerSavesTheLog() throws Exception {
        final ExecutionContext ctx = run(SETS_LAYER);

        assertEquals("IOS", ctx.layer());
        assertEquals("IOS", metadata.getLayer(), "the layer statement writes the rule's metadata");
        assertEquals(List.of("IOS"), saved);
    }

    @Test
    void aRuleWithoutALayerIsNotSavedUnderAnotherRulesLayer() throws Exception {
        run(SETS_LAYER);
        final ExecutionContext noLayer = run(SETS_NO_LAYER);

        assertNull(noLayer.layer());
        assertFalse(noLayer.shouldSave(), "dropped, though the metadata holds the other rule's IOS");
        assertEquals(List.of("IOS"), saved, "saved once, by the rule that decided the layer");
    }

    @Test
    void aRuleWithoutALayerIsDroppedWhicheverRuleRunsFirst() throws Exception {
        final ExecutionContext noLayer = run(SETS_NO_LAYER);
        run(SETS_LAYER);

        assertFalse(noLayer.shouldSave());
        assertEquals(List.of("IOS"), saved);
    }

    @Test
    void anEmptyLayerIsNoLayer() throws Exception {
        final ExecutionContext ctx = run("filter { extractor { layer sourceAttribute(\"absent\") } sink {} }");

        assertTrue(ctx.layer() == null || ctx.layer().isEmpty());
        assertFalse(ctx.shouldSave());
        assertTrue(saved.isEmpty());
    }

    @Test
    void aLayerRoutedRuleThatSetsAnEmptyLayerIsDropped() throws Exception {
        metadata.setLayer("GENERAL");
        final ExecutionContext ctx = runRouted(
            "filter { extractor { layer sourceAttribute(\"absent\") } sink {} }", "{}");

        assertFalse(ctx.shouldSave(), "dropped, not thrown by Layer.valueOf in the sink");
        assertTrue(saved.isEmpty());
    }

    @Test
    void aLayerRoutedRuleThatSetsANullLayerIsDropped() throws Exception {
        metadata.setLayer("GENERAL");
        final ExecutionContext ctx = runRouted(KIND_LAYER, "{}");

        assertTrue(ctx.isLayerDecided(), "the statement ran, though its value is null");
        assertNull(ctx.layer());
        assertNull(metadata.getLayer());
        assertFalse(ctx.shouldSave());
        assertTrue(saved.isEmpty());
    }

    @Test
    void aRuleThatSetsAnUnregisteredLayerIsDropped() throws Exception {
        metadata.setLayer("GENERAL");
        final ExecutionContext routed = runRouted(KIND_LAYER, "{\"kind\":\"FOO\"}");
        metadata.setLayer("");
        final ExecutionContext auto = run(KIND_LAYER, "{\"kind\":\"IOSS\"}");

        assertFalse(routed.shouldSave());
        assertFalse(auto.shouldSave(), "a non-empty layer passes the auto check, but is not registered");
        assertTrue(saved.isEmpty());
    }

    @Test
    void aRuleThatSetsARegisteredComputedLayerSavesTheLog() throws Exception {
        metadata.setLayer("GENERAL");
        runRouted(KIND_LAYER, "{\"kind\":\"IOS\"}");

        assertEquals(List.of("IOS"), saved);
    }

    @Test
    void aLayerRoutedRuleWithoutALayerStatementSavesUnderTheArrivedLayer() throws Exception {
        metadata.setLayer("GENERAL");
        final ExecutionContext ctx = runRouted("filter { sink {} }", "{}");

        assertFalse(ctx.isLayerDecided());
        assertEquals(List.of("GENERAL"), saved);
    }

    private ExecutionContext run(final String dsl) throws Exception {
        return run(dsl, "{}");
    }

    /**
     * Run in auto mode, as a rule of the auto or the fallback pass.
     */
    private ExecutionContext run(final String dsl, final String jsonBody) throws Exception {
        return execute(dsl, jsonBody, true);
    }

    /**
     * Run as a rule declared for the log's layer.
     */
    private ExecutionContext runRouted(final String dsl, final String jsonBody) throws Exception {
        return execute(dsl, jsonBody, false);
    }

    private ExecutionContext execute(final String dsl, final String jsonBody,
                                     final boolean autoMode) throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        generator.setOutputType(LogBuilder.class);
        final LalExpression expr = generator.compile(dsl);
        final LogData.Builder input = LogData.newBuilder()
                                             .setService("svc")
                                             .setBody(LogDataBody.newBuilder()
                                                                 .setJson(JSONLog.newBuilder().setJson(jsonBody)));
        final ExecutionContext ctx = new ExecutionContext().init(metadata, input);
        ctx.autoLayerMode(autoMode);
        expr.execute(filterSpec, ctx);
        return ctx;
    }

    private FilterSpec filterSpec() throws Exception {
        final ModuleManager manager = mock(ModuleManager.class);
        setField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));
        final ModuleProviderHolder logHolder = mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider logProvider = mock(LogAnalyzerModuleProvider.class);
        when(logProvider.getMetricConverts()).thenReturn(Collections.emptyList());
        when(logHolder.provider()).thenReturn(logProvider);
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(logHolder);
        final ModuleProviderHolder coreHolder = mock(ModuleProviderHolder.class);
        when(coreHolder.provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);

        final FilterSpec spec = new FilterSpec(manager, new LogAnalyzerModuleConfig());
        final LogSinkListenerFactory recording = () -> new LogSinkListener() {
            private String layer;

            @Override
            public LogSinkListener parse(final LogMetadata metadata, final Object input, final ExecutionContext ctx) {
                layer = metadata.getLayer();
                return this;
            }

            @Override
            public void build() {
                saved.add(layer);
            }
        };
        setField(spec, "sinkListenerFactories", List.of(recording));
        return spec;
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
