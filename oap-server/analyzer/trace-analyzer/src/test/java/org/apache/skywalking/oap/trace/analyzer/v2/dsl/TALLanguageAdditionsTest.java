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

import com.google.common.hash.Hashing;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The TAL language additions every input has: plain assignment, typed Layer and enum values,
 * {@code foreach}, {@code log}, getters evaluated where they stand, the functions for every input
 * and contributed functions. Each rule writes what it computed into {@code meta.service}, which the test reads.
 */
class TALLanguageAdditionsTest {
    private static final String SCRIPT_LOGGER = "org.apache.skywalking.trace.analysis.script.test-additions";
    private static final List<TALSourceTypeProvider> INPUTS = List.of(
        new TestInputProvider(), new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider());

    private ModuleManager moduleManager;
    private TALFunctionRegistry functions;
    private FilterSpec filterSpec;
    private SourceReceiver sourceReceiver;

    @BeforeEach
    void setUp() {
        moduleManager = mock(ModuleManager.class);
        when(moduleManager.has(anyString())).thenReturn(false);

        functions = new TALFunctionRegistry(List.of(new TestFunctionProvider(),
            new TestNativeSideProvider(), new TestMetadataSideProvider()));
        functions.bind(null, INPUTS);

        final NamingControl namingControl = new NamingControl(70, 70, 150, new EndpointNameGrouping());
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        sourceReceiver = mock(SourceReceiver.class);
        filterSpec = TALTestModules.filterSpec(moduleManager, coreProvider, mock(MeterSystem.class), namingControl,
            sourceReceiver);
    }

    @AfterEach
    void tearDown() {
        detachCapture();
    }

    // ==================== Assignment ====================

    @Test
    void plainAssignmentReassignsADeclaredVariable() throws Exception {
        final String rule = "filter { extractor {\n"
            + "  def ok = \"yes\"\n"
            + "  if (span.name == \"bad\") {\n"
            + "    ok = \"no\"\n"
            + "  }\n"
            + "  service ok\n"
            + "} }";
        assertEquals("no", run(rule, input("bad")));
        assertEquals("yes", run(rule, input("good")));
    }

    @Test
    void assigningAnUndeclaredNameIsACompileError() {
        final Exception e = assertThrows(Exception.class,
            () -> compile("filter { extractor { ok = \"x\"\n service ok } }"));
        assertTrue(e.getMessage().contains("no def or segmentDef declares"), e.getMessage());
    }

    @Test
    void aLaterDefOfTheSameNameStillAssigns() throws Exception {
        assertEquals("second", run("filter { extractor {\n"
            + "  def v = \"first\"\n"
            + "  def v = \"second\"\n"
            + "  service v\n"
            + "} }", input("x")));
    }

    // ==================== Typed Layer and enum values ====================

    @Test
    void typedLayerAndEnumValuesCompareByName() throws Exception {
        assertEquals("typed", run("filter { extractor {\n"
            + "  def callee = \"GENERAL\" as Layer\n"
            + "  def type = \"HTTP\" as RequestType\n"
            + "  def owner = null as String\n"
            + "  def noLayer = null as Layer\n"
            + "  if (callee == \"GENERAL\" && type != \"RPC\" && owner == null && noLayer == null) {\n"
            + "    service \"typed\"\n"
            + "  }\n"
            + "} }", input("x")));
    }

    @Test
    void aMisspeltLayerOrEnumLiteralIsACompileError() {
        final Exception layer = assertThrows(Exception.class,
            () -> compile("filter { extractor { def l = \"GENRAL\" as Layer\n service \"x\" } }"));
        assertTrue(layer.getMessage().contains("names no registered layer"), layer.getMessage());
        final Exception comparison = assertThrows(Exception.class,
            () -> compile("filter { extractor { def t = \"HTTP\" as RequestType\n"
                + " if (t == \"HTTPS\") { service \"x\" } } }"));
        assertTrue(comparison.getMessage().contains("HTTPS"), comparison.getMessage());
    }

    // ==================== foreach ====================

    @Test
    void foreachWalksAListInOrderWithPerIterationDefs() throws Exception {
        final TestNestedSpanInput in = input("x");
        in.getNames().addAll(List.of("a", "b", "c"));
        assertEquals("ab!c", run("filter { extractor {\n"
            + "  def joined = \"\"\n"
            + "  foreach (n in span.names) {\n"
            + "    if (n == \"b\") {\n"
            + "      def seen = \"!\"\n"
            + "    }\n"
            + "    joined = joined + n\n"
            + "    if (seen != null) {\n"
            + "      joined = joined + seen\n"
            + "    }\n"
            + "  }\n"
            + "  service joined\n"
            + "} }", in));
    }

    @Test
    void foreachTypesItsLoopVariableToTheElementType() throws Exception {
        final TestNestedSpanInput in = input("x");
        in.getChildren().addAll(List.of(new TestSpanChild("p"), new TestSpanChild("q")));
        assertEquals("pq", run("filter { extractor {\n"
            + "  def joined = \"\"\n"
            + "  foreach (child in span.children) {\n"
            + "    joined = joined + child.label\n"
            + "  }\n"
            + "  service joined\n"
            + "} }", in));
    }

    @Test
    void foreachRefusesWhatIsNotAListAndHidesItsVariableAfterTheBody() {
        final Exception notList = assertThrows(Exception.class,
            () -> compile("filter { extractor { foreach (n in span.name) { } } }"));
        assertTrue(notList.getMessage().contains("walks a java.util.List"), notList.getMessage());
        assertThrows(Exception.class,
            () -> compile("filter { extractor { foreach (n in span.names) { }\n service n } }"));
    }

    // ==================== Getters where they stand ====================

    @Test
    void aGuardedChainIsNotDereferencedBeforeItsGuard() throws Exception {
        final String rule = "filter { extractor {\n"
            + "  def label = \"none\"\n"
            + "  if (span.child != null) {\n"
            + "    label = span.child.label\n"
            + "  }\n"
            + "  service label\n"
            + "} }";
        assertEquals("none", run(rule, input("x")));
        final TestNestedSpanInput withChild = input("x");
        withChild.setChild(new TestSpanChild("kid"));
        assertEquals("kid", run(rule, withChild));
    }

    // ==================== Functions for every input ====================

    @Test
    void functionsForEveryInputCallTheJavaTheyName() throws Exception {
        final TestNestedSpanInput in = input("x");
        in.setStartTime(1_700_000_000_390L);
        final String expected = IDManager.ServiceID.buildId("svc", true)
            + "|" + TimeBucket.getMinuteTimeBucket(1_700_000_000_390L)
            + "|3|abc|true|v|0|42|2|0";
        assertEquals(expected, run("filter { extractor {\n"
            + "  def id = buildServiceId(\"svc\", true)\n"
            + "  def bucket = minuteTimeBucket(span.startTime)\n"
            + "  def rounded = round(2.6)\n"
            + "  def cutText = cut(\"abcdef\", 3)\n"
            + "  def blank = isBlank(\"  \")\n"
            + "  def json = parseJsonObject(\"{\\\"k\\\":\\\"v\\\"}\")\n"
            + "  def jsonValue = json.get(\"k\").getAsString()\n"
            + "  def bad = parseLongOrZero(\"x1\")\n"
            + "  def good = parseIntOrZero(\"42\")\n"
            + "  def mod = floorMod(17, 5)\n"
            + "  def list = emptyList()\n"
            + "  service id + \"|\" + bucket + \"|\" + rounded + \"|\" + cutText + \"|\" + blank + \"|\" + jsonValue\n"
            + "    + \"|\" + bad + \"|\" + good + \"|\" + mod + \"|\" + list.size()\n"
            + "} }", in));
    }

    @Test
    void murmur3HashIsGuavasMurmur3OfTheUtf8Bytes() throws Exception {
        assertEquals(String.valueOf(Hashing.murmur3_128().hashString("trace-1", StandardCharsets.UTF_8).asLong()),
            run("filter { extractor { service \"\" + murmur3Hash(\"trace-1\") } }", input("x")));
    }

    @Test
    void theLenientParsesWarnUnderTheScriptLogger() throws Exception {
        final List<LogEvent> events = capture(Level.WARN);
        run("filter { extractor { def n = parseLongOrZero(\"oops\")\n service \"\" + n } }", input("x"));
        assertEquals(1, events.size());
        assertEquals("Failed to parse value to long: {}", events.get(0).getMessage().getFormat());
        assertEquals(SCRIPT_LOGGER, events.get(0).getLoggerName());
    }

    // ==================== log ====================

    @Test
    void logWritesUnderTheScriptLoggerWithItsArguments() throws Exception {
        final List<LogEvent> events = capture(Level.DEBUG);
        run("filter { extractor { log debug \"name {} and {}\", span.name, 42\n service \"x\" } }", input("svc"));
        assertEquals(1, events.size());
        assertEquals(SCRIPT_LOGGER, events.get(0).getLoggerName());
        assertEquals("name svc and 42", events.get(0).getMessage().getFormattedMessage());
        assertEquals("org.apache.skywalking.trace.analysis.script.native-rpc",
            TALClassGenerator.scriptLoggerName(DslSourceRef.ofRule("tal/native-rpc.yaml", 3)));
    }

    @Test
    void anUnknownLogLevelIsACompileError() {
        final Exception e = assertThrows(Exception.class,
            () -> compile("filter { extractor { log loud \"x\" } }"));
        assertTrue(e.getMessage().contains("level"), e.getMessage());
    }

    // ==================== Contributed functions ====================

    @Test
    void contributedFunctionsReceiveTheRunsContextAndTypedResults() throws Exception {
        assertEquals("hi you from svc/42/xy", run("filter { extractor {\n"
            + "  def joined = \"\"\n"
            + "  foreach (p in pair(\"x\", \"y\")) {\n"
            + "    joined = joined + p\n"
            + "  }\n"
            + "  service greet(\"you\") + \"/\" + twice(21) + \"/\" + joined\n"
            + "} }", input("svc")));
    }

    @Test
    void aFunctionNameTwoProvidersServeFailsTheBind() {
        final TALFunctionRegistry both = new TALFunctionRegistry(
            List.of(new TestFunctionProvider(), new TestDuplicateFunctionProvider()));
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> both.bind(null, INPUTS));
        assertTrue(e.getMessage().contains("twice"), e.getMessage());
    }

    @Test
    void anEveryInputFunctionOverlapsAnInputScopedOneOfTheSameName() {
        final TALFunctionRegistry overlapping = new TALFunctionRegistry(
            List.of(new TestFunctionProvider(), new TestNativeTwiceProvider()));
        final IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> overlapping.bind(null, INPUTS));
        assertTrue(e.getMessage().contains("twice") && e.getMessage().contains("sw-native"), e.getMessage());
    }

    @Test
    void eachSideDeclaresItsOwnFunctionOfOneName() throws Exception {
        assertEquals("metadata", run("filter { extractor { service side() } }", input("x")));
        // A native rule has no metadata to write into, so it reports the native side's answer by aborting.
        assertNull(run("filter { if (side() == \"native\") { abort {} } }", input("x"),
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE));
    }

    @Test
    void aFunctionOfAnotherInputIsReportedAsThatInputs() {
        final Exception nativeInOtlp = assertThrows(Exception.class,
            () -> compile("filter { extractor { service nativeOnly() } }", "otlp"));
        assertTrue(nativeInOtlp.getMessage().contains("nativeOnly() is a function of the inputs [sw-native]")
            && nativeInOtlp.getMessage().contains("inputType otlp"), nativeInOtlp.getMessage());
        for (final String otlpOnly : List.of("attr(\"k\")", "resource(\"k\")", "scopeAttr(\"k\")",
                                             "eventAttr(\"e\", \"k\")", "resolveNetworkAlias(\"a\")",
                                             "findServerClusterName(\"a\")")) {
            final Exception e = assertThrows(Exception.class,
                () -> compile("filter { if (" + otlpOnly + " == \"x\") { abort {} } }",
                    TALFunctionProvider.SW_NATIVE_INPUT_TYPE), otlpOnly);
            assertTrue(e.getMessage().contains("is a function of the inputs [otlp, test]")
                && e.getMessage().contains("inputType sw-native"), e.getMessage());
        }
    }

    @Test
    void aConstructOfAnotherInputIsReportedAsThatInputs() {
        final String nativeAlias = TALFunctionProvider.SW_NATIVE_INPUT_TYPE;
        final Map<String, String> misused = Map.of(
            "filter { extractor { service \"x\" } }", "service belongs to the inputs [otlp]",
            "filter { if (meta.service == \"x\") { abort {} } }", "the meta root belongs to the inputs [otlp]",
            "filter { if (pairing.peer == \"x\") { abort {} } }", "the pairing root belongs to the inputs [otlp]",
            "filter { extractor { tag \"k\": \"v\" } }", "tag belongs to the inputs [otlp]",
            "filter { persist { enforcer {} } }", "persist {} belongs to the inputs [otlp]");
        for (final Map.Entry<String, String> rule : misused.entrySet()) {
            final Exception e = assertThrows(Exception.class, () -> compile(rule.getKey(), nativeAlias), rule.getKey());
            assertTrue(e.getMessage().contains(rule.getValue()) && e.getMessage().contains("inputType sw-native"),
                e.getMessage());
        }
    }

    @Test
    void eachInputDeclaresItsConstructsAndFunctions() {
        final SwNativeTALSourceTypeProvider nativeInput = new SwNativeTALSourceTypeProvider();
        final OtlpTALSourceTypeProvider otlpInput = new OtlpTALSourceTypeProvider();
        assertEquals(TALConstruct.NATIVE_SEGMENT, nativeInput.constructs());
        assertEquals(TALConstruct.SPAN_METADATA, otlpInput.constructs());
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        registry.bind(null, List.of(nativeInput, otlpInput));
        assertSame(nativeInput, registry.candidates("tag", 1, TALFunctionProvider.SW_NATIVE_INPUT_TYPE).get(0).getOwner());
        assertSame(otlpInput, registry.candidates("attr", 1, "otlp").get(0).getOwner());
        // A span reader takes the span itself, not the run, so it is not a function a rule calls.
        assertNull(registry.candidates("spanAttributes", 1, "otlp"));
    }

    @Test
    void aNativeRuleReadsTagsWhereAnOtlpRuleReadsAttributes() throws Exception {
        final SpanObject span = SpanObject.newBuilder()
            .addTags(KeyStringValuePair.newBuilder().setKey("db.type").setValue("first"))
            .addTags(KeyStringValuePair.newBuilder().setKey("db.type").setValue("Mysql"))
            .build();
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").build(), span, Collections.emptyMap(), Collections.emptyMap());
        ctx.setSourceTypeProvider(new SwNativeTALSourceTypeProvider());
        final TALClassGenerator generator = generator(TALFunctionProvider.SW_NATIVE_INPUT_TYPE, "tags");
        generator.setInputType(SpanObject.class);
        generator.compile("filter { if (tag(\"db.type\") == \"first\" && hasTag(\"db.type\") && !hasTag(\"x\")) {"
            + " abort {} } }").execute(filterSpec, ctx);
        assertTrue(ctx.isAborted());
        final Exception tagInOtlp = assertThrows(Exception.class,
            () -> compile("filter { if (tag(\"k\") == \"v\") { abort {} } }", "otlp"));
        assertTrue(tagInOtlp.getMessage().contains("tag() is a function of the inputs [sw-native]"),
            tagInOtlp.getMessage());
    }

    @Test
    void sharedFunctionsServeEveryInput() throws Exception {
        final List<LogEvent> events = capture(Level.WARN);
        final TestNestedSpanInput in = input("svc");
        assertEquals("404/absent", run("filter { extractor {\n"
            + "  def ok = parseStatusCode(\"404\")\n"
            + "  def bad = parseStatusCode(\"x\")\n"
            + "  def shown = \"set\"\n"
            + "  if (bad == null) {\n"
            + "    shown = \"absent\"\n"
            + "  }\n"
            + "  service ok + \"/\" + shown\n"
            + "} }", in));
        assertEquals(1, events.size());
        assertTrue(events.get(0).getMessage().getFormattedMessage().endsWith("has illegal status code x"));
        compile("filter { if (isGateway(\"gw:80\") && parseStatusCode(\"1\") != null) { abort {} } }",
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE);
    }

    @Test
    void theOtlpSideTakesAnyNumberOfAttributeKeys() throws Exception {
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").build(), input("x"), Map.of("e", "five", "a", "one"),
            Collections.emptyMap());
        compile("filter { extractor { service resource(\"z\", \"y\", \"x\", \"w\", \"e\") + \"/\""
            + " + resource(\"a\") + \"/\" + eventAttr(\"none\") } }", "otlp").execute(filterSpec, ctx);
        assertEquals("five/one/", ctx.getMeta().getService());
    }

    @Test
    void aContributedFunctionCalledWithTheWrongArgumentCountIsACompileError() {
        final Exception e = assertThrows(Exception.class,
            () -> compile("filter { extractor { service \"\" + twice(1, 2) } }"));
        assertTrue(e.getMessage().contains("does not take 2"), e.getMessage());
    }

    // ==================== Module contracts ====================

    // ==================== Typed values, conversions and overloads ====================

    @Test
    void primitiveLogArgumentsAreBoxed() throws Exception {
        final List<LogEvent> events = capture(Level.DEBUG);
        final TestNestedSpanInput in = input("svc");
        in.setStartTime(7L);
        run("filter { log debug \"{} {} {} {} {}\", round(2.6), 1, 2 as String, (span.startTime as Long),"
            + " span.flagged\n extractor { service \"x\" } }", in);
        assertEquals("3 1 2 7 false", events.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void typedCastsConvertWhereverTheyStand() throws Exception {
        assertEquals("GENERAL/GENERAL/SERVER", run("filter { extractor {\n"
            + "  def l = (\"GENERAL\" as Layer)\n"
            + "  service layerName(l) + \"/\" + layerName(\"GENERAL\") + \"/\" + pointName(\"SERVER\")\n"
            + "} }", input("x")));
        final Exception paren = assertThrows(Exception.class,
            () -> compile("filter { extractor { def l = (\"BAD\" as Layer)\n service \"x\" } }"));
        assertTrue(paren.getMessage().contains("names no registered layer"), paren.getMessage());
        final Exception argument = assertThrows(Exception.class,
            () -> compile("filter { extractor { service pointName(\"SIDEWAYS\") } }"));
        assertTrue(argument.getMessage().contains("none of its constants"), argument.getMessage());
    }

    @Test
    void sourceFieldsConvertToTheirSettersTypes() throws Exception {
        if (DefaultScopeDefine.classOf("TestTypedSource") == null) {
            new DefaultScopeDefine.Listener().notify(TestTypedSource.class);
        }
        final String dsl = "filter { extractor {\n"
            + "  source TestTypedSource {\n"
            + "    kind \"GENERAL\"\n"
            + "    point \"SERVER\" as DetectPoint\n"
            + "    hits \"41\"\n"
            + "    rate 2.9 as Double\n"
            + "    names span.names\n"
            + "    timeBucket 1\n"
            + "  }\n"
            + "} }";
        final TalExpression rule = generator("test", dsl).compile(dsl);
        final TestNestedSpanInput in = input("x");
        in.getNames().add("a");
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").build(), in, Collections.emptyMap(), Collections.emptyMap());
        rule.execute(filterSpec, ctx);
        final ArgumentCaptor<ISource> sent = ArgumentCaptor.forClass(ISource.class);
        verify(sourceReceiver).receive(sent.capture());
        final TestTypedSource out = (TestTypedSource) sent.getValue();
        assertEquals("GENERAL", out.getKind().name());
        assertEquals("SERVER", out.getPoint().name());
        assertEquals(Integer.valueOf(41), out.getHits());
        assertEquals(2L, out.getRate());
        assertEquals(List.of("a"), out.getNames());
    }

    @Test
    void anOverloadIsChosenByItsArgumentsAndTypesTheCall() throws Exception {
        assertEquals("m/3/s:a/l:5", run("filter { extractor {\n"
            + "  def text = mixed()\n"
            + "  service text + \"/\" + mixed(2) + \"/\" + pick(\"a\") + \"/\" + pick(5)\n"
            + "} }", input("x")));
        final Exception tie = assertThrows(Exception.class,
            () -> compile("filter { extractor { service pick(thing()) } }"));
        assertTrue(tie.getMessage().contains("equally well"), tie.getMessage());
        assertEquals("l:7", run("filter { extractor { service pick(\"7\" as Long) } }",
            input("x")));
    }

    @Test
    void stringContextsApplyTheRulesCastFirst() throws Exception {
        assertEquals("GENERAL", run("filter { extractor { service \"GENERAL\" as Layer } }", input("x")));
        final Exception bad = assertThrows(Exception.class,
            () -> compile("filter { extractor { service \"BAD\" as Layer } }"));
        assertTrue(bad.getMessage().contains("names no registered layer"), bad.getMessage());
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").build(), input("x"), Map.of("3", "three", "true", "yes"),
            Collections.emptyMap());
        compile("filter { extractor { service resource(round(2.6)) + \"/\" + resource(round(2.6) as Boolean) } }",
            "otlp").execute(filterSpec, ctx);
        assertEquals("three/yes", ctx.getMeta().getService());
    }

    @Test
    void comparisonsApplyTheirCasts() throws Exception {
        assertNull(run("filter {\n"
            + "  if (\"GENERAL\" as Layer == \"GENERAL\" && round(2.6) as Long == 3"
            + " && isBlank(\"\") as Boolean == true && (\"SERVER\" as DetectPoint) == \"SERVER\") { abort {} }\n"
            + "  extractor { service \"kept\" }\n"
            + "}", input("x")));
        final Exception literal = assertThrows(Exception.class,
            () -> compile("filter { if (\"BAD\" as Layer == \"GENERAL\") { abort {} } }"));
        assertTrue(literal.getMessage().contains("names no registered layer"), literal.getMessage());
        final Exception kinds = assertThrows(Exception.class,
            () -> compile("filter { if (\"GENERAL\" as Layer == \"SERVER\" as DetectPoint) { abort {} } }"));
        assertTrue(kinds.getMessage().contains("cannot compare"), kinds.getMessage());
    }

    @Test
    void bareConditionsAndStringComparisonsConvertAndBox() throws Exception {
        assertNull(run("filter { if (round(2.6)) { abort {} }\n extractor { service \"kept\" } }", input("x")));
        assertNull(run("filter { if (amount() as String == \"2.6\") { abort {} }\n extractor { service \"kept\" } }",
            input("x")));
        final Exception typed = assertThrows(Exception.class,
            () -> compile("filter { if (\"BAD\" as Layer) { abort {} } }"));
        assertTrue(typed.getMessage().contains("names no registered layer"), typed.getMessage());
    }

    @Test
    void nestedExpressionsKeepTheirOwnTypes() throws Exception {
        final List<LogEvent> events = capture(Level.INFO);
        final TestNestedSpanInput in = input("svc");
        in.setStartTime(5L);
        in.getNames().add("a");
        assertEquals("0/3/2.6/Integer:3/false/true", run("filter {\n"
            + "  if (readObject(span?.startTime)) { log info \"{}\", span.name.length() }\n"
            + "  extractor {\n"
            + "    def s = \"abc\"\n"
            + "    def n = (round(2.6))\n"
            + "    def blank = (isBlank(\"\"))\n"
            + "    def t = (amount()) as String\n"
            + "    def xs = span.names\n"
            + "    service (s?.indexOf(97 as Integer) as String) + \"/\" + n + \"/\" + t + \"/\""
            + " + any(span.name.length()) + \"/\" + xs.contains(round(2.6)) + \"/\" + blank\n"
            + "  }\n"
            + "}", in));
        assertEquals("3", events.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void chainsResolveFromTheirRootAndKeepTheirResultTypes() throws Exception {
        final TestNestedSpanInput in = input("abc");
        in.setChild(new TestSpanChild("xy"));
        in.getNames().add("four");
        in.setBytes(new byte[] {65, 66});
        assertEquals("0/xyxy/4/65/65/7", run("filter { extractor {\n"
            + "  def c = span.child\n"
            + "  def joined = \"\"\n"
            + "  foreach (x in c.repeat(2)) {\n"
            + "    joined = joined + x\n"
            + "  }\n"
            + "  def total = 0\n"
            + "  foreach (l in c.lengths(span.names)) {\n"
            + "    total = total + l\n"
            + "  }\n"
            + "  def s = small()\n"
            + "  service (span.getName().indexOf(97 as Integer) as String) + \"/\" + joined + \"/\" + total"
            + " + \"/\" + (span.bytes[0] as String) + \"/\" + (span.name.toUpperCase().getBytes()[0] as String)"
            + " + \"/\" + s\n"
            + "} }", in));
    }

    @Test
    void parenthesizedChainsArraysAndNarrowValuesKeepTheirTypes() throws Exception {
        final TestNestedSpanInput in = input("abc");
        in.setBytes(new byte[] {65});
        assertEquals("0/98/65/66/8", run("filter {\n"
            + "  if (span.name.charAt(0) != span.name.charAt(0)) { abort {} }\n"
            + "  extractor {\n"
            + "    def bytes = span.bytes\n"
            + "    def head = \"\"\n"
            + "    foreach (chunk in chunks()) {\n"
            + "      head = chunk[0] as String\n"
            + "    }\n"
            + "    service ((span).getName().indexOf(97 as Integer) as String) + \"/\""
            + " + ((span.name).substring(1 as Integer).getBytes()[0] as String) + \"/\" + (bytes[0] as String)"
            + " + \"/\" + head + \"/\" + (small() + 1)\n"
            + "  }\n"
            + "}", in));
    }

    @Test
    void localChainsObjectChainsAndBoxedNarrowValuesTakePartAsTheirTypes() throws Exception {
        final List<LogEvent> events = capture(Level.INFO);
        final TestNestedSpanInput in = input("x");
        in.setChild(new TestSpanChild("c"));
        assertEquals("3.0/8/byte:5", run("filter {\n"
            + "  log info \"{}\", (thing()).hashCode()\n"
            + "  extractor {\n"
            + "    def c = span.child\n"
            + "    service (2 * c.weight) + \"/\" + (boxedSmall() + 1) + \"/\" + takeByte(5)\n"
            + "  }\n"
            + "}", in));
        assertEquals(String.valueOf("abc".hashCode()), events.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void arithmeticReadsTheTypeOfTheMethodTheCodeCalls() throws Exception {
        final TestNestedSpanInput in = input("x");
        in.setChild(new TestSpanChild("c"));
        in.setBytes(new byte[] {65});
        assertEquals("4/4/b5/s6", run("filter { extractor {\n"
            + "  service (span.name.length() + 1) + \"/\" + ((span.name).length() + 1) + \"/\" + readByte(5) + \"/\""
            + " + readShort(6)\n"
            + "} }", input("abc")));
        assertEquals("text1/4.0/66/3.5/3/8", run("filter { extractor {\n"
            + "  def c = span.child\n"
            + "  def bytes = span.bytes\n"
            + "  def n = 2\n"
            + "  service (c.value(\"x\") + 1) + \"/\" + (c.value(2) + 1) + \"/\" + (bytes[0] + 1) + \"/\""
            + " + (c.amount + 1) + \"/\" + ((n) + 1) + \"/\" + ((boxedSmall()) + 1)\n"
            + "} }", in));
    }

    @Test
    void anUnregisteredInputHasTheOtlpInputsFunctionsNotAnotherInputs() {
        final OtlpTALSourceTypeProvider child = new OtlpTALSourceTypeProvider() {
            @Override
            public String alias() {
                return "child";
            }

            public String childOnly(final TalRuntimeHelper h) {
                return "child";
            }
        };
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        registry.bind(null, List.of(child, new OtlpTALSourceTypeProvider()));
        assertNotNull(registry.candidates("childOnly", 0, "child"));
        final Exception e = assertThrows(Exception.class, () -> registry.candidates("childOnly", 0, "unregistered"));
        assertTrue(e.getMessage().contains("the inputs [child]"), e.getMessage());
    }

    @Test
    void aMetricScopeReadsTheEntityTheCurrentLoopDeclared(@TempDir final Path dir) throws Exception {
        final String dsl = "filter { extractor {\n"
            + "  foreach (n in span.names) {\n"
            + "    def svc = service(n, \"GENERAL\")\n"
            + "    metrics { name \"first\"; scope svc; function avg; value 1 }\n"
            + "  }\n"
            + "  foreach (n in span.names) {\n"
            + "    def svc = service(n, \"GENERAL\")\n"
            + "    metrics { name \"second\"; scope svc; function avg; value 1 }\n"
            + "  }\n"
            + "} }";
        final TALClassGenerator generator = generator("test", dsl);
        generator.setClassOutputDir(dir.toFile());
        generator.compile(dsl);
        final String source;
        try (Stream<Path> files = Files.walk(dir)) {
            final Path java = files.filter(f -> f.toString().endsWith(".java")).findFirst().orElseThrow();
            source = Files.readString(java);
        }
        // The second loop's metrics block reads its own loop's entity: the source after the second loop starts
        // names the redeclared local and never the first loop's.
        final String secondLoop = source.substring(source.lastIndexOf("while ("));
        assertTrue(secondLoop.contains("_def_svc_1") && !secondLoop.replace("_def_svc_1", "").contains("_def_svc"),
            secondLoop);
    }

    @Test
    void aRateLimitPlaceholderKeepsItsCast() throws Exception {
        assertEquals("kept", run("filter { extractor { service \"kept\" }\n"
            + " persist { sampler { rateLimit(\"r-${span.name as Layer}\") { rpm 100 } } } }", input("GENERAL")));
        assertThrows(Exception.class, () -> run("filter { extractor { service \"kept\" }\n"
            + " persist { sampler { rateLimit(\"r-${span.name as Layer}\") { rpm 100 } } } }", input("NO_SUCH_LAYER")));
    }

    @Test
    void literalConditionsApplyTheirCasts() throws Exception {
        assertEquals(run("filter { if (false as String) { abort {} }\n extractor { service \"kept\" } }", input("x")),
            run("filter { if ((false as String)) { abort {} }\n extractor { service \"kept\" } }", input("x")));
        assertThrows(Exception.class,
            () -> run("filter { if (true as Layer) { abort {} } }", input("x")));
    }

    @Test
    void metricAndRelationArgumentsConvert() throws Exception {
        compile("filter { extractor {\n"
            + "  def svc = service(\"s\", \"GENERAL\")\n"
            + "  def rel = serviceRelation(SERVER, \"a\", \"GENERAL\", \"b\", \"GENERAL\", \"2.6\" as Double)\n"
            + "  metrics { name \"amount\"; scope svc; function avg; value amount() }\n"
            + "} }");
        final Exception value = assertThrows(Exception.class, () -> compile("filter { extractor {\n"
            + "  def svc = service(\"s\", \"GENERAL\")\n"
            + "  metrics { name \"bad\"; scope svc; function avg; value \"BAD\" as Layer }\n"
            + "} }"));
        assertTrue(value.getMessage().contains("takes a long, not a Layer"), value.getMessage());
        final Exception point = assertThrows(Exception.class, () -> compile("filter { extractor {\n"
            + "  def rel = serviceRelation(\"CLIENT\" as Layer, \"a\", \"GENERAL\", \"b\", \"GENERAL\", 0)\n"
            + "  service \"x\"\n"
            + "} }"));
        assertTrue(point.getMessage().contains("CLIENT or SERVER"), point.getMessage());
    }

    @Test
    void jsonBuiltInsKeepTheirArgumentsCast() {
        final Exception json = assertThrows(Exception.class,
            () -> compile("filter { extractor { def j = toJson(\"BAD\" as Layer)\n service \"x\" } }"));
        assertTrue(json.getMessage().contains("names no registered layer"), json.getMessage());
        final Exception array = assertThrows(Exception.class,
            () -> compile("filter { extractor { def j = toJsonArray(\"SIDEWAYS\" as DetectPoint)\n service \"x\" } }"));
        assertTrue(array.getMessage().contains("none of its constants"), array.getMessage());
    }

    @Test
    void twoInputsMayEachHoldAnInstanceOfOneFunctionClass() {
        final SwNativeTALSourceTypeProvider second = new SwNativeTALSourceTypeProvider() {
            @Override
            public String alias() {
                return "sw-native-2";
            }
        };
        final TALFunctionRegistry registry = new TALFunctionRegistry(List.of());
        registry.bind(null, List.of(new SwNativeTALSourceTypeProvider(), second));
        final TALFunctionRegistry.Resolved first = registry.candidates("tag", 1, "sw-native").get(0);
        final TALFunctionRegistry.Resolved other = registry.candidates("tag", 1, "sw-native-2").get(0);
        assertNotSame(first.getOwner(), other.getOwner());
        assertSame(first.getOwner(), registry.instance(first.getOwnerKey()));
        assertSame(other.getOwner(), registry.instance(other.getOwnerKey()));
    }

    @Test
    void builtInArgumentsApplyTheirCasts() {
        final Exception component = assertThrows(Exception.class,
            () -> compile("filter { extractor { service \"\" + component(\"BAD\" as Layer) } }"));
        assertTrue(component.getMessage().contains("names no registered layer"), component.getMessage());
        final Exception urlHost = assertThrows(Exception.class,
            () -> compile("filter { extractor { service urlHost(\"https://example.test/p\" as Layer) } }"));
        assertTrue(urlHost.getMessage().contains("names no registered layer"), urlHost.getMessage());
        final Exception attrKey = assertThrows(Exception.class,
            () -> compile("filter { extractor { service attr(\"k\" as Layer) } }"));
        assertTrue(attrKey.getMessage().contains("names no registered layer"), attrKey.getMessage());
    }

    @Test
    void aTypedArgumentPassesWhereTheParameterTakesItsType() throws Exception {
        assertEquals("Layer:GENERAL/Layer:GENERAL", run("filter { extractor {\n"
            + "  service any(\"GENERAL\" as Layer) + \"/\" + any((\"GENERAL\" as Layer))\n"
            + "} }", input("x")));
        final Exception e = assertThrows(Exception.class,
            () -> compile("filter { extractor { service \"\" + twice(\"GENERAL\" as Layer) } }"));
        assertTrue(e.getMessage().contains("takes a long, not a Layer"), e.getMessage());
    }

    @Test
    void aParenthesizedTypedValueTypesTheMethodsAfterIt() throws Exception {
        assertEquals("SERVER/general", run("filter { extractor {\n"
            + "  def n = (\"GENERAL\" as Layer).name()\n"
            + "  service (\"SERVER\" as DetectPoint).name() + \"/\" + n.toLowerCase()\n"
            + "} }", input("x")));
    }

    @Test
    void aClassNameMaySpellAKeywordComponent() throws Exception {
        assertEquals("SERVER", run("filter { extractor {\n"
            + "  def p = \"SERVER\" as org.apache.skywalking.oap.server.core.source.DetectPoint\n"
            + "  service pointName(p)\n"
            + "} }", input("x")));
    }

    @Test
    void anOverloadOfTheArgumentsOwnTypeBeatsASupertype() throws Exception {
        final TestNestedSpanInput in = input("x");
        in.setChild(new TestSpanChild("c"));
        assertEquals("s/s/o/o", run("filter { extractor {\n"
            + "  service choose(\"x\") + \"/\" + choose(\"x\" as String) + \"/\" + choose(span.child)"
            + " + \"/\" + choose(thing())\n"
            + "} }", in));
    }

    @Test
    void anAssignmentKeepsTheListsElementType() {
        final Exception e = assertThrows(Exception.class, () -> compile("filter { extractor {\n"
            + "  def children = span.children\n"
            + "  children = span.names\n"
            + "  service \"x\"\n"
            + "} }"));
        assertTrue(e.getMessage().contains("holds a list of TestSpanChild"), e.getMessage());
    }

    @Test
    void foreachWalksAnEmptyListOfObjects() throws Exception {
        assertEquals("none", run("filter { extractor {\n"
            + "  def seen = \"none\"\n"
            + "  foreach (x in emptyList()) {\n"
            + "    seen = \"some\"\n"
            + "  }\n"
            + "  service seen\n"
            + "} }", input("x")));
    }

    @Test
    void samplerBodiesTakeForeachAndLog() throws Exception {
        final List<LogEvent> events = capture(Level.INFO);
        final TestNestedSpanInput in = input("svc");
        in.getNames().add("a");
        in.getNames().add("b");
        run("filter { extractor { service \"x\" }\n"
            + " persist { sampler {\n"
            + "   foreach (n in span.names) { log info \"name {}\", n }\n"
            + "   log info \"done\"\n"
            + "   rateLimit(\"r\") { rpm 100 }\n"
            + " } } }", in);
        assertEquals(3, events.size());
        assertEquals("name a", events.get(0).getMessage().getFormattedMessage());
        assertEquals("done", events.get(2).getMessage().getFormattedMessage());
    }

    @Test
    void aSkippedRuleFileRegistersNoLayer() throws Exception {
        final TALConfigs skipped = TALConfigs.parse("optional",
            ("requiredModules:\n  - absent-module\nlayerDefinitions:\n  - name: TAL_SKIPPED_FILE_LAYER\n"
                + "    ordinal: 9917\nrules:\n  - name: r\n    layer: TAL_SKIPPED_FILE_LAYER\n"
                + "    dsl: |\n      filter { abort {} }\n").getBytes(StandardCharsets.UTF_8),
            "optional.yaml", moduleManager);
        assertNull(skipped);
        assertThrows(RuntimeException.class, () -> Layer.valueOf("TAL_SKIPPED_FILE_LAYER"));
    }

    @Test
    void aRuleFileRunsOnlyWithTheModulesItRequires() throws Exception {
        final TALConfigs file = TALConfigs.parse("genai",
            ("requiredModules:\n  - gen-ai-analyzer\nrules:\n  - name: r\n    layer: GENERAL\n"
                + "    dsl: |\n      filter { abort {} }\n").getBytes(StandardCharsets.UTF_8), "genai.yaml");
        assertNotNull(file);
        assertEquals(List.of("gen-ai-analyzer"), file.getRequiredModules());
        assertFalse(file.requiredModulesLoaded(moduleManager));
        when(moduleManager.has("gen-ai-analyzer")).thenReturn(true);
        assertTrue(file.requiredModulesLoaded(moduleManager));
    }

    @Test
    void theJsonFunctionsWorkWhereverAValueGoes() throws Exception {
        // Helper functions like any other: chained in a condition, not only as a def's initializer.
        assertEquals("yes", run("filter { extractor {\n"
            + "  if (toJson(span.name).has(\"a\") && toJsonArray(\"[1, 2]\").size() == 2) {\n"
            + "    service \"yes\"\n"
            + "  }\n"
            + "} }", input("{\"a\": 1}")));
        // A null argument has one overload to call: an empty object or array, as before.
        assertEquals("{}/[]", run("filter { extractor {\n"
            + "  def j = toJson(null)\n"
            + "  def a = toJsonArray(null)\n"
            + "  service j.toString() + \"/\" + a.toString()\n"
            + "} }", input("x")));
    }

    // ==================== Harness ====================

    private static TestNestedSpanInput input(final String name) {
        final TestNestedSpanInput in = new TestNestedSpanInput();
        in.setName(name);
        return in;
    }

    private TalExpression compile(final String dsl) throws Exception {
        return compile(dsl, "test");
    }

    private TalExpression compile(final String dsl, final String inputAlias) throws Exception {
        return generator(inputAlias, dsl).compile(dsl);
    }

    private TALClassGenerator generator(final String inputAlias, final String dsl) {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("additions_" + Math.abs((inputAlias + dsl).hashCode()));
        generator.setInputType(TestNestedSpanInput.class);
        generator.setInputAlias(inputAlias);
        generator.setFunctionRegistry(functions);
        generator.setSourceRef(DslSourceRef.ofRule("tal/test-additions.yaml", 1));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        return generator;
    }

    private String run(final String dsl, final TestNestedSpanInput input) throws Exception {
        return run(dsl, input, "test");
    }

    private String run(final String dsl, final TestNestedSpanInput input, final String inputAlias)
        throws Exception {
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").build(), input, Collections.emptyMap(), Collections.emptyMap());
        compile(dsl, inputAlias).execute(filterSpec, ctx);
        return ctx.isAborted() ? null : ctx.getMeta().getService();
    }

    // ==================== Log capture ====================

    private CapturingAppender appender;

    private List<LogEvent> capture(final Level level) {
        // On the logger's configuration, which every Logger instance of the name reads, the generated class's
        // SLF4J one included; a level set on one instance would not reach it.
        Configurator.setLevel(SCRIPT_LOGGER, level);
        final Logger logger = (Logger) LogManager.getLogger(SCRIPT_LOGGER);
        appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);
        return appender.events;
    }

    private void detachCapture() {
        if (appender != null) {
            final Logger logger = (Logger) LogManager.getLogger(SCRIPT_LOGGER);
            logger.removeAppender(appender);
            appender.stop();
            appender = null;
        }
    }

    private static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        private CapturingAppender() {
            super("tal-script-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
