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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.Segment;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The constructs of the native input, compiled and run against protobuf spans: the segment's variables, the
 * {@code first {}} run, queued {@code build {}} blocks and what they send, and the alias row sent during the span loop.
 * The lifecycle around them, which span runs what and when the queued blocks run, is the native runner's.
 */
class TALNativeConstructsTest {
    private static final long START = 1_700_000_000_000L;

    private final List<ISource> sent = new ArrayList<>();
    private FilterSpec filterSpec;
    private TALFunctionRegistry functions;
    private SwNativeSegmentState currentSegment;

    @BeforeAll
    static void declareScopes() {
        final DefaultScopeDefine.Listener scopes = new DefaultScopeDefine.Listener();
        for (final Class<?> source : List.of(Segment.class, ServiceMeta.class, TestOverloadedSource.class)) {
            if (DefaultScopeDefine.classOf(source.getSimpleName()) == null) {
                scopes.notify(source);
            }
        }
    }

    @BeforeEach
    void setUp() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(sourceReceiver).receive(any());
        filterSpec = TALTestModules.filterSpec(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), sourceReceiver);
        final TestRefineProvider refine = new TestRefineProvider();
        when(moduleManager.has(refine.module())).thenReturn(true);
        functions = new TALFunctionRegistry(List.of(refine));
        functions.bind(moduleManager, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        TestOverloadedSource.CALLS.clear();
    }

    // ==================== segmentDef ====================

    @Test
    void aSegmentVariableLivesAcrossTheSegmentsSpansAndIsTypedBeforeItsStatement() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  if (count == null) {\n"
            + "    segmentDef count = 0\n"
            + "  }\n"
            + "  count = count + 1\n"
            + "  extractor {\n"
            + "    if (span.spanId == 2) {\n"
            + "      segmentDef last = span.operationName\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertEquals(2, rule.segmentDefCount());
        final SwNativeSegmentState segment = segment(span(0, "op0"), span(1, "op1"), span(2, "op2"));
        final Object[] defs = new Object[rule.segmentDefCount()];
        for (final SpanObject span : segment.getSegmentObject().getSpansList()) {
            rule.execute(filterSpec, run(span, defs));
        }
        // An Integer counted, not the text "0" + "1" concatenated: the read before the statement has its type.
        assertArrayEquals(new Object[] {3, "op2"}, defs);

        final Object[] nextSegment = new Object[rule.segmentDefCount()];
        rule.execute(filterSpec, run(segment.getSegmentObject().getSpans(0), nextSegment));
        assertArrayEquals(new Object[] {1, null}, nextSegment);
    }

    @Test
    void aSegmentVariableReadBeforeItsDeclarationIsTypedWhereverTheDeclarationStands() throws Exception {
        // Read in the regular run, declared in first {}, a method generated after it; read in a branch before the
        // statement in the same method; walked by a foreach before the statement that gives it a list.
        final TalExpression rule = compile("filter {\n"
            + "  first {\n"
            + "    segmentDef label = span.operationName\n"
            + "  }\n"
            + "  def n = 0\n"
            + "  if (label != null) {\n"
            + "    n = label.length()\n"
            + "  }\n"
            + "  if (later != null) {\n"
            + "    n = n + later.length()\n"
            + "  }\n"
            + "  segmentDef later = span.operationName + \"!\"\n"
            + "  if (keys != null) {\n"
            + "    foreach (key in keys) {\n"
            + "      n = n + key.length()\n"
            + "    }\n"
            + "  }\n"
            + "  segmentDef keys = tagList()\n"
            + "  segmentDef total = n\n"
            + "}");
        final SpanObject span = segment(span(0, "op0").toBuilder()
            .addTags(KeyStringValuePair.newBuilder().setKey("k").setValue("v")).build()).getSegmentObject().getSpans(0);
        final Object[] defs = new Object[rule.segmentDefCount()];
        rule.executeFirst(filterSpec, run(span, defs));
        rule.execute(filterSpec, run(span, defs));
        assertEquals(3, defs[3], "label only; later and keys are not set yet on the first run");
        rule.execute(filterSpec, run(span, defs));
        assertEquals(3 + 4 + 3, defs[3], "label, later (op0!) and the tag k:v");
    }

    @Test
    void aSegmentVariableDeclaredInsideABlockWhoseConditionReadsItIsTyped() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  first {\n"
            + "    label = span.operationName\n"
            + "    spanTags = span.tagsList\n"
            + "  }\n"
            + "  if (label.length() > 0) {\n"
            + "    segmentDef label = span.operationName + \"!\"\n"
            + "  }\n"
            + "  foreach (spanTag in spanTags) {\n"
            + "    segmentDef spanTags = span.tagsList\n"
            + "    segmentDef lastKey = spanTag.key\n"
            + "  }\n"
            + "}");
        final SpanObject span = segment(span(0, "op0").toBuilder()
            .addTags(KeyStringValuePair.newBuilder().setKey("k").setValue("v")).build()).getSegmentObject().getSpans(0);
        final Object[] defs = new Object[rule.segmentDefCount()];
        rule.executeFirst(filterSpec, run(span, defs));
        rule.execute(filterSpec, run(span, defs));
        assertEquals("op0!", defs[0]);
        assertEquals("k", defs[2]);
    }

    @Test
    void theGeneratedCodeWaitsForTheSegmentVariableTypesToSettle() throws Exception {
        // Each generation refines items one step, Collection, List, then ArrayList; an unrelated variable must not
        // change which overload the settled code calls.
        for (final String extra : List.of("", "  segmentDef unused = 0\n")) {
            final TalExpression rule = compile("filter {\n"
                + extra
                + "  segmentDef items = refine(items)\n"
                + "  segmentDef picked = choose(items)\n"
                + "}");
            final Object[] defs = new Object[rule.segmentDefCount()];
            rule.execute(filterSpec, run(segment(span(0, "op0")).getSegmentObject().getSpans(0), defs));
            assertTrue(List.of(defs).contains("arraylist"), "picked " + List.of(defs));
        }
    }

    @Test
    void aClassNameCastIsTheTypeTheCompiledCallPasses() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  def items = refine(null as java.util.List)\n"
            + "  segmentDef picked = choose(items as java.util.List)\n"
            + "  segmentDef described = describe(tagList() as java.util.Collection)\n"
            + "}");
        final Object[] defs = new Object[rule.segmentDefCount()];
        rule.execute(filterSpec, run(segment(span(0, "op0")).getSegmentObject().getSpans(0), defs));
        assertArrayEquals(new Object[] {"list", "collection"}, defs);
    }

    @Test
    void eachGeneratedMethodHasItsOwnEntityVariables() throws Exception {
        compile("filter {\n"
            + "  def ent = service(\"s\", \"GENERAL\")\n"
            + "  first {\n"
            + "    def ent = endpoint(\"s\", \"e\", \"GENERAL\")\n"
            + "  }\n"
            + "  extractor {\n"
            + "    build { def other = service(\"s\", \"GENERAL\") }\n"
            + "    build { def other = endpoint(\"s\", \"e\", \"GENERAL\") }\n"
            + "  }\n"
            + "}");
        // An entity held in a segment variable keeps its scope in every method that reads it.
        compile("filter {\n"
            + "  segmentDef owner = service(\"s\", \"GENERAL\")\n"
            + "  extractor {\n"
            + "    build { metrics { name \"owned\"; scope owner; function sum; value 1 } }\n"
            + "  }\n"
            + "}");
    }

    @Test
    void aNameIsADefOrASegmentVariableNotBoth() {
        final Exception e = assertThrows(Exception.class, () -> compile("filter {\n"
            + "  segmentDef seen = true\n"
            + "  def seen = false\n"
            + "}"));
        assertTrue(e.getMessage().contains("def seen names the rule's segmentDef seen"), e.getMessage());
    }

    // ==================== first ====================

    @Test
    void firstIsARunOfItsOwnThatSharesOnlyTheSegmentVariables() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  def local = \"regular\"\n"
            + "  first {\n"
            + "    def local = \"first\"\n"
            + "    segmentDef seen = local\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    if (seen != null) {\n"
            + "      segmentDef after = seen + \"/\" + local\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertTrue(rule.hasFirst());
        final SpanObject span = segment(span(0, "op0")).getSegmentObject().getSpans(0);
        final Object[] defs = new Object[rule.segmentDefCount()];
        final ExecutionContext first = run(span, defs);
        rule.executeFirst(filterSpec, first);
        assertTrue(first.isAborted());
        final ExecutionContext regular = run(span, defs);
        rule.execute(filterSpec, regular);
        assertFalse(regular.isAborted());
        assertArrayEquals(new Object[] {"first", "first/regular"}, defs);

        assertFalse(compile("filter { def x = 1 }").hasFirst());
    }

    @Test
    void firstBelongsDirectlyInTheFilter() {
        final Exception e = assertThrows(Exception.class,
            () -> compile("filter { if (span.spanType == \"Entry\") { first { def x = 1 } } }"));
        assertTrue(e.getMessage().contains("first {} belongs directly in filter {}"), e.getMessage());
    }

    // ==================== build ====================

    @Test
    void aBuildBlockRunsLaterWithTheValuesItsRunHadWhenItReachedIt() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  def opName = span.operationName\n"
            + "  extractor {\n"
            + "    foreach (spanTag in span.tagsList) {\n"
            + "      build {\n"
            + "        opName = opName + \":\" + spanTag.key\n"
            + "        source ServiceMeta {\n"
            + "          name opName\n"
            + "          layer \"FAAS\"\n"
            + "          timeBucket minuteTimeBucket(span.startTime)\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "    opName = \"changed\"\n"
            + "  }\n"
            + "}");
        final SpanObject span = segment(span(0, "op0").toBuilder()
            .addTags(KeyStringValuePair.newBuilder().setKey("k1").setValue("v1"))
            .addTags(KeyStringValuePair.newBuilder().setKey("k2").setValue("v2")).build()).getSegmentObject().getSpans(0);
        final ExecutionContext ctx = run(span, new Object[0]);
        rule.execute(filterSpec, ctx);
        assertTrue(sent.isEmpty(), "a run sends nothing from a build {} block");
        final List<QueuedBuild> queued = new ArrayList<>(ctx.getQueuedBuilds());
        assertEquals(2, queued.size());

        // The first block twice: its assignment changed its own copy, not the captured values.
        for (final QueuedBuild build : List.of(queued.get(0), queued.get(0), queued.get(1))) {
            rule.executeBuild(build.getIndex(), filterSpec, run((SpanObject) build.getInput(), new Object[0]),
                build.getCaptured());
        }
        assertEquals(List.of("op0:k1", "op0:k1", "op0:k2"), names(sent));
        final ServiceMeta meta = (ServiceMeta) sent.get(0);
        assertEquals(Layer.FAAS, meta.getLayer());
        assertEquals(TimeBucket.getMinuteTimeBucket(START), meta.getTimeBucket());
    }

    @Test
    void anAbortInsideABuildBlockStopsThatBlockAndAnAbortAfterItIsLeftToTheRunner() throws Exception {
        final TalExpression rule = compile("filter {\n"
            + "  extractor {\n"
            + "    build {\n"
            + "      source ServiceMeta { name \"before\" timeBucket 1 }\n"
            + "      if (span.spanId == 0) {\n"
            + "        abort {}\n"
            + "      }\n"
            + "      source ServiceMeta { name \"after\" timeBucket 1 }\n"
            + "    }\n"
            + "  }\n"
            + "  abort {}\n"
            + "}");
        final SpanObject span = segment(span(0, "op0")).getSegmentObject().getSpans(0);
        final ExecutionContext ctx = run(span, new Object[0]);
        rule.execute(filterSpec, ctx);
        // The runner withdraws the blocks of an aborted run; the run only reports both.
        assertTrue(ctx.isAborted());
        assertEquals(1, ctx.getQueuedBuilds().size());
        final QueuedBuild build = ctx.getQueuedBuilds().get(0);
        rule.executeBuild(build.getIndex(), filterSpec, run(span, new Object[0]), build.getCaptured());
        assertEquals(List.of("before"), names(sent));
    }

    @Test
    void outputsOfANativeRuleBelongInsideBuild() {
        final Map<String, String> outside = Map.of(
            "filter { extractor { source ServiceMeta { name \"a\" timeBucket 1 } } }", "source ServiceMeta {} belongs inside build {}",
            "filter { extractor { metrics { name \"m\"; scope service(\"s\", \"GENERAL\"); function count; value 1 } } }",
            "metrics {} belongs inside build {}",
            "filter { extractor { build { build { def x = 1 } } } }", "build {} cannot sit inside another build {}",
            "filter { if (span.spanType == \"Entry\") { build { def x = 1 } } }", "build {} belongs inside extractor {}");
        for (final Map.Entry<String, String> rule : outside.entrySet()) {
            final Exception e = assertThrows(Exception.class, () -> compile(rule.getKey()), rule.getKey());
            assertTrue(e.getMessage().contains(rule.getValue()), e.getMessage());
        }
    }

    // ==================== source ====================

    @Test
    void aSourceNeedsATimeBucketAndANameTheOalEngineDispatches() {
        final Exception noBucket = assertThrows(Exception.class,
            () -> compile("filter { extractor { build { source ServiceMeta { name \"a\" } } } }"));
        assertTrue(noBucket.getMessage().contains("source ServiceMeta {} sets no timeBucket"), noBucket.getMessage());
        final Exception unknown = assertThrows(Exception.class,
            () -> compile("filter { extractor { build { source Nope { timeBucket 1 } } } }"));
        assertTrue(unknown.getMessage().contains("source Nope {} names no declared source")
            && unknown.getMessage().contains("ServiceMeta"), unknown.getMessage());
        final Exception noField = assertThrows(Exception.class,
            () -> compile("filter { extractor { build { source ServiceMeta { nope 1 timeBucket 1 } } } }"));
        assertTrue(noField.getMessage().contains("source ServiceMeta {} has no field 'nope'"), noField.getMessage());
        final Exception badLayer = assertThrows(Exception.class,
            () -> compile("filter { extractor { build { source ServiceMeta { layer \"NOPE\" timeBucket 1 } } } }"));
        assertTrue(badLayer.getMessage().contains("NOPE"), badLayer.getMessage());
    }

    @Test
    void aSourceFieldChoosesTheSetterItsValueFits() throws Exception {
        final SpanObject span = segment(span(0, "op0")).getSegmentObject().getSpans(0);
        final TalExpression overloads = compile("filter { extractor { build {\n"
            + "  source TestOverloadedSource { payload tagList() payload tagMap() timeBucket 1 }\n"
            + "} } }");
        final ExecutionContext overloaded = run(span, new Object[0]);
        overloads.execute(filterSpec, overloaded);
        TestOverloadedSource.CALLS.clear();
        final QueuedBuild overloadedBuild = overloaded.getQueuedBuilds().get(0);
        overloads.executeBuild(overloadedBuild.getIndex(), filterSpec, run(span, new Object[0]),
            overloadedBuild.getCaptured());
        assertEquals(List.of("payload list of 0", "payload map of 0"), TestOverloadedSource.CALLS);
        final Exception ambiguous = assertThrows(Exception.class, () -> compile("filter { extractor { build {\n"
            + "  def unknown = null\n"
            + "  source TestOverloadedSource { payload unknown timeBucket 1 }\n"
            + "} } }"));
        assertTrue(ambiguous.getMessage().contains("field 'payload' has setters taking [Collection, List, Map]"),
            ambiguous.getMessage());

        final TalExpression cast = compile("filter { extractor { build {\n"
            + "  def anything = tagList() as java.lang.Object\n"
            + "  source TestOverloadedSource {\n"
            + "    payload anything as java.util.List\n"
            + "    payload tagList() as java.util.Collection\n"
            + "    timeBucket 1\n"
            + "  }\n"
            + "} } }");
        final ExecutionContext castRun = run(span, new Object[0]);
        cast.execute(filterSpec, castRun);
        TestOverloadedSource.CALLS.clear();
        final QueuedBuild castBuild = castRun.getQueuedBuilds().get(0);
        cast.executeBuild(castBuild.getIndex(), filterSpec, run(span, new Object[0]), castBuild.getCaptured());
        assertEquals(List.of("payload list of 0", "payload collection of 0"), TestOverloadedSource.CALLS);
    }

    // ==================== writeNetworkAlias ====================

    @Test
    void writeNetworkAliasSendsTheRowNow() throws Exception {
        final TalExpression rule = compile("filter { extractor {\n"
            + "  foreach (ref in span.refsList) {\n"
            + "    writeNetworkAlias formatServiceName(ref.networkAddressUsedAtPeer)\n"
            + "  }\n"
            + "} }");
        final SpanObject span = segment(span(0, "op0").toBuilder()
            .setSpanType(SpanType.Entry)
            .addRefs(SegmentReference.newBuilder().setRefType(RefType.CrossProcess)
                                     .setNetworkAddressUsedAtPeer("10.0.0.1:8080")).build()).getSegmentObject().getSpans(0);
        rule.execute(filterSpec, run(span, new Object[0]));
        assertEquals(1, sent.size());
        final NetworkAddressAliasSetup alias = (NetworkAddressAliasSetup) sent.get(0);
        alias.prepare();
        assertEquals("10.0.0.1:8080", alias.getAddress());
        assertEquals(IDManager.ServiceID.buildId("svc", true), alias.getRepresentServiceId());
        assertEquals(IDManager.ServiceInstanceID.buildId(IDManager.ServiceID.buildId("svc", true), "inst"),
            alias.getRepresentServiceInstanceId());
        assertEquals(TimeBucket.getMinuteTimeBucket(START), alias.getTimeBucket());
    }

    // ==================== Inputs ====================

    @Test
    void theNativeConstructsBelongToTheNativeInput() {
        final Map<String, String> otlp = Map.of(
            "filter { extractor { build { def x = 1 } } }", "build {} belongs to the inputs [sw-native]",
            "filter { first { def x = 1 } }", "first {} belongs to the inputs [sw-native]",
            "filter { segmentDef x = 1 }", "segmentDef belongs to the inputs [sw-native]",
            "filter { extractor { writeNetworkAlias \"a\" } }", "writeNetworkAlias belongs to the inputs [sw-native]");
        for (final Map.Entry<String, String> rule : otlp.entrySet()) {
            final Exception e = assertThrows(Exception.class, () -> compile(rule.getKey(), "otlp"), rule.getKey());
            assertTrue(e.getMessage().contains(rule.getValue()) && e.getMessage().contains("inputType otlp"),
                e.getMessage());
        }
    }

    // ==================== Harness ====================

    private static List<String> names(final List<ISource> sources) {
        final List<String> names = new ArrayList<>();
        for (final ISource source : sources) {
            names.add(((ServiceMeta) source).getName());
        }
        return names;
    }

    private static SpanObject span(final int spanId, final String operationName) {
        return SpanObject.newBuilder().setSpanId(spanId).setParentSpanId(spanId - 1)
                         .setOperationName(operationName).setStartTime(START).setEndTime(START + 10).build();
    }

    /**
     * @return the segment run of these spans, which later runs read as their segment
     */
    private SwNativeSegmentState segment(final SpanObject... spans) {
        final SegmentObject.Builder segment = SegmentObject.newBuilder()
            .setService("svc").setServiceInstance("inst").setTraceId("t1").setTraceSegmentId("seg1");
        for (final SpanObject span : spans) {
            segment.addSpans(span);
        }
        currentSegment = new SwNativeSegmentState(segment.build());
        return currentSegment;
    }

    private ExecutionContext run(final SpanObject span, final Object[] segmentDefs) {
        final ExecutionContext ctx = new ExecutionContext();
        ctx.setSourceTypeProvider(new SwNativeTALSourceTypeProvider());
        ctx.bindNativeRun(SpanMetadata.builder().startTime(span.getStartTime()).build(), span, currentSegment,
            segmentDefs);
        return ctx;
    }

    private TalExpression compile(final String dsl) throws Exception {
        return compile(dsl, TALFunctionProvider.SW_NATIVE_INPUT_TYPE);
    }

    private TalExpression compile(final String dsl, final String inputAlias) throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("native_" + Math.abs((inputAlias + dsl).hashCode()));
        generator.setInputType(SpanObject.class);
        generator.setInputAlias(inputAlias);
        generator.setFunctionRegistry(functions);
        generator.setSourceRef(DslSourceRef.ofRule("tal/test-native.yaml", 1));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        return generator.compile(dsl);
    }
}
