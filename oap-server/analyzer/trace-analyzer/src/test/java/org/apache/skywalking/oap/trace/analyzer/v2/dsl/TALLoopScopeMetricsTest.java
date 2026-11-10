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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.junit.jupiter.api.AfterEach;
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

/**
 * A metrics block inside a {@code foreach} sends its observation for the entity its own iteration declared, also when
 * an earlier loop declared an entity of the same name.
 */
class TALLoopScopeMetricsTest {
    private final List<AcceptableValue<?>> sent = new ArrayList<>();
    private MeterSystem meterSystem;
    private FilterSpec filterSpec;
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;

    @BeforeEach
    void setUp() throws Exception {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        Mockito.when(coreModule.provider()).thenReturn(coreProvider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        meterSystem = spy(new MeterSystem(moduleManager));
        final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
        doNothing().when(processor).create(any(), (StreamDefinition) any(), any(), any());
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(meterSystem).doStreamingCalculation(any());
        final NamingControl namingControl = new NamingControl(70, 70, 150, new EndpointNameGrouping());
        MeterEntity.setNamingControl(namingControl);
        filterSpec = TALTestModules.filterSpec(moduleManager, coreProvider, meterSystem, namingControl,
            mock(SourceReceiver.class));
    }

    @AfterEach
    void tearDown() {
        mockedProcessor.close();
    }

    @Test
    void eachLoopsMetricsGoToTheEntityItsOwnIterationDeclared() throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("loop_scope_metrics");
        generator.setMetricPrefix("tal_loop_scope");
        generator.setInputType(TestNestedSpanInput.class);
        generator.setSourceRef(DslSourceRef.ofRule("tal/test-loop-scope.yaml", 1));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        final TalExpression rule = generator.compile("filter { extractor {\n"
            + "  foreach (n in span.names) {\n"
            + "    def svc = service(n, \"GENERAL\")\n"
            + "    metrics { name \"loop_first\"; scope svc; function avg; value 1 }\n"
            + "  }\n"
            + "  foreach (n in span.children) {\n"
            + "    def svc = service(n.label, \"GENERAL\")\n"
            + "    metrics { name \"loop_second\"; scope svc; function avg; value 1 }\n"
            + "  }\n"
            + "} }");
        generator.createMetrics(meterSystem);

        final TestNestedSpanInput input = new TestNestedSpanInput();
        input.getNames().add("first");
        input.getNames().add("second");
        input.getChildren().add(new TestSpanChild("third"));
        input.getChildren().add(new TestSpanChild("fourth"));
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("svc").startTime(1L).timestamp(2L).build(), input,
            Collections.emptyMap(), Collections.emptyMap());
        rule.execute(filterSpec, ctx);

        final List<String> entities = new ArrayList<>();
        for (final AcceptableValue<?> value : sent) {
            entities.add(((Meter) value).getEntityId());
        }
        final List<String> expected = new ArrayList<>();
        for (final String service : List.of("first", "second", "third", "fourth")) {
            expected.add(IDManager.ServiceID.buildId(service, true));
        }
        assertEquals(expected, entities);
    }
}
