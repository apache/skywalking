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

import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListenerFactory;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.TrafficSinkListener;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.dsl.debug.ToJson;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A rule either runs every statement as written, with Groovy LAL's semantics, or fails to compile naming what it
 * does not support.
 */
class LALRunsAsWrittenTest {
    private FilterSpec filterSpec;
    private final List<SampleFamily> metrics = new ArrayList<>();
    private int saved;

    @BeforeEach
    void setUp() throws Exception {
        filterSpec = filterSpec();
    }

    // ==================== Statements, blocks and conditions ====================

    @Test
    void abortInAnExtractorIfAbortsTheRestOfTheRule() throws Exception {
        final String dsl = "filter { extractor { if (tag(\"k\") == \"x\") { abort {} } tag t: \"after\" } sink {} }";

        final Run aborted = run(dsl, log().tag("k", "x"));
        assertTrue(aborted.ctx.shouldAbort());
        assertTrue(aborted.output.tags.isEmpty());
        assertEquals(0, saved);

        final Run kept = run(dsl, log().tag("k", "y"));
        assertEquals(List.of("t=after"), kept.output.tags);
        assertEquals(1, saved);
    }

    @Test
    void statementOutsideItsBlockIsRejected() {
        assertRejected("filter { if (tag(\"k\") == \"x\") { layer \"GENERAL\" } sink {} }", "not supported in a filter");
        assertRejected("filter { if (tag(\"k\") == \"x\") { enforcer {} } sink {} }", "not supported in a filter");
        assertRejected("filter { extractor { if (tag(\"k\") == \"x\") { json {} } } sink {} }",
            "not supported in an extractor");
        assertRejected("filter { sink { if (tag(\"k\") == \"x\") { abort {} } } }", "use dropper {}");
        assertRejected("filter { sink { sampler { if (tag(\"k\") == \"x\") { enforcer {} } } } }",
            "not supported in a sampler");
    }

    @Test
    void onlyALayerStatementAnExtractorRunsCounts() {
        assertFalse(LALScriptParser.parse("filter { if (tag(\"k\") == \"x\") { layer \"GENERAL\" } sink {} }")
                                   .assignsLayer());
        assertTrue(LALScriptParser.parse(
            "filter { if (tag(\"k\") == \"x\") { extractor { if (tag(\"j\") == \"y\") { layer \"GENERAL\" } } } "
                + "sink {} }").assignsLayer());
    }

    @Test
    void nestedIfInASinkIfRuns() throws Exception {
        final String dsl = "filter { sink { if (tag(\"a\") == \"x\") { enforcer {} "
            + "if (tag(\"b\") == \"y\") { dropper {} } } } }";

        assertFalse(run(dsl, log().tag("a", "x").tag("b", "y")).ctx.shouldSave());
        assertTrue(run(dsl, log().tag("a", "x").tag("b", "z")).ctx.shouldSave());
    }

    @Test
    void notBindsTighterThanAndAndOr() throws Exception {
        final String dsl = "filter { if (!tag(\"a\") && tag(\"b\")) { abort {} } sink {} }";

        assertFalse(run(dsl, log()).ctx.shouldAbort());
        assertTrue(run(dsl, log().tag("b", "1")).ctx.shouldAbort());
        assertRejected("filter { if (!tag(\"a\") == \"x\") { abort {} } sink {} }", "ambiguous");
    }

    @Test
    void bareConditionUsesGroovyTruthOfTheCastValue() throws Exception {
        final String cast = "filter { json {} if (parsed.n as Integer) { abort {} } sink {} }";
        assertFalse(run(cast, log().json("{\"n\":\"0\"}")).ctx.shouldAbort());
        assertTrue(run(cast, log().json("{\"n\":\"3\"}")).ctx.shouldAbort());

        final String flag = "filter { json {} if (parsed.flag) { abort {} } sink {} }";
        assertFalse(run(flag, log().json("{\"flag\":false}")).ctx.shouldAbort());
        assertTrue(run(flag, log().json("{\"flag\":true}")).ctx.shouldAbort());

        final String has = "filter { json {} extractor { def m = toJson(parsed.m) "
            + "if (m?.has(\"env\")) { tag env: \"yes\" } } sink {} }";
        assertTrue(run(has, log().json("{\"m\":{\"x\":1}}")).output.tags.isEmpty());
    }

    @Test
    void unresolvableNameIsRejected() {
        assertRejected("filter { json {} extractor { service prased.instance as String } sink {} }",
            "Unknown name 'prased'");
        assertRejected("filter { json {} extractor { service lower(parsed.a).trim() } sink {} }",
            "Unknown function 'lower");
        assertRejected("filter { json {} extractor { def x = parsed.a } extractor { service x as String } sink {} }",
            "def 'x' is used outside the block that declares it");
        assertRejected("filter { json {} extractor { def x = parsed.a } sink { sampler { rateLimit(\"${x.b}\") "
            + "{ rpm 1 } } } }", "def 'x' is used outside the block that declares it");
    }

    @Test
    void javassistOrJvmRefusalFailsTheCompileNamingTheRule() throws Exception {
        final ClassPool pool = new ClassPool(true);
        final LALClassGenerator generator = new LALClassGenerator(pool);
        final CtClass broken = pool.makeClass(LalExpressionPackageHolder.class.getPackageName() + ".BrokenRule");
        final IllegalArgumentException early = assertThrows(IllegalArgumentException.class,
            () -> generator.makeMethod("public void execute() { noSuchMethod(); }", broken, "lal/x.yaml:3"));
        assertTrue(early.getMessage().contains("LAL rule lal/x.yaml:3 does not compile"), early.getMessage());

        // A class whose initialization fails: linking errors are Errors, which the compile turns into a refusal.
        final CtClass failing = pool.makeClass(LalExpressionPackageHolder.class.getPackageName() + ".FailingRule");
        failing.addInterface(pool.get(LalExpression.class.getName()));
        failing.makeClassInitializer().setBody("{ Integer.parseInt(\"x\"); }");
        final IllegalArgumentException late = assertThrows(IllegalArgumentException.class,
            () -> generator.instantiate(failing, "lal/y.yaml:7"));
        assertTrue(late.getMessage().contains("LAL rule lal/y.yaml:7 compiled to a class the JVM refuses"),
            late.getMessage());
        assertTrue(late.getCause() instanceof LinkageError, String.valueOf(late.getCause()));

        assertRejected("filter { json {} extractor { tag t: parsed.x.noSuchMethod() } sink {} }",
            "Cannot resolve method Object.noSuchMethod()");
    }

    @Test
    void distributedExampleCompiles() throws Exception {
        final Path example = Paths.get("..", "..", "..", "dist-material", "config-examples", "lal.yaml");
        final Map<String, Object> config = new Yaml().load(Files.readString(example));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> rules = (List<Map<String, Object>>) config.get("rules");
        assertFalse(rules.isEmpty());
        for (final Map<String, Object> rule : rules) {
            final Run run = run((String) rule.get("dsl"), log().text(
                "2026-11-20 10:00:00.000 [TID:t1] [main] INFO  o.a.s.Example - started"));
            assertFalse(run.ctx.shouldAbort(), (String) rule.get("name"));
            assertEquals("log_count", onlySample().getName());
        }
    }

    // ==================== parsed.* and parsers ====================

    @Test
    void ruleMayMixATextParserWithAJsonOne() throws Exception {
        final String dsl = "filter { if (tag(\"k\") == \"text\") { text { regexp \"(?<a>\\w+)\" } } else { json {} } "
            + "extractor { tag a: parsed.a } sink {} }";
        assertEquals(List.of("a=word"), run(dsl, log().tag("k", "text").text("word")).output.tags);
        assertEquals(List.of("a=7"), run(dsl, log().json("{\"a\":7}")).output.tags);
    }

    @Test
    void repeatedRegexpIsRejected() {
        assertRejected("filter { text { regexp \"(?<a>.*)\" regexp \"(?<b>.*)\" } sink {} }", "takes one regexp");
    }

    @Test
    void aRegexpKeepsItsEscapesAsWritten() throws Exception {
        // \s reaches the regexp as written, any whitespace: a string is the text between its quotes
        final String dsl = "filter { text { regexp \"^a\\sb$\" } sink {} }";

        assertFalse(run(dsl, log().text("a b")).ctx.shouldAbort());
        assertFalse(run(dsl, log().text("a\tb")).ctx.shouldAbort());
    }

    @Test
    void abortOnFailureIsAFlagOnEitherSideOfRegexp() throws Exception {
        assertFalse(run("filter { text { regexp \"ok\" abortOnFailure false } sink {} }", log().text("no"))
            .ctx.shouldAbort());
        assertTrue(run("filter { text { regexp \"ok\" abortOnFailure true } sink {} }", log().text("no"))
            .ctx.shouldAbort());
        assertRejected("filter { text { abortOnFailure false regexp \"ok\" abortOnFailure true } sink {} }",
            "both true and false");
    }

    @Test
    void aTextJoinsOnlyOntoATextANumberACharacterOrNull() throws Exception {
        assertRejected("filter { extractor { tag a: tag(\"s\").split(\",\") + \"-x\" } sink {} }",
            "A String[] takes no part in a text +");
        assertRejected("filter { extractor { tag a: \"x\" + tag(\"s\").split(\",\") } sink {} }",
            "A String[] takes no part in a text +");
        // typed only at run time: the log fails
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run(
            "filter { json {} extractor { tag a: \"x\" + parsed.xs } sink {} }", log().json("{\"xs\":[1,2]}")));
        assertTrue(e.getMessage().contains("takes no part in a text +"), e.getMessage());
    }

    @Test
    void aTextGroupIsAStringTheRestOfTheChainReads() throws Exception {
        // the first name reads the group; the rest is the string's, a property-spelled call included
        assertEquals(List.of("a=WARN", "b=WARN", "c=warn", "d=4"), run("filter { text { regexp \"(?<level>\\w+) .*\" } "
            + "extractor { tag a: parsed.level.toString, b: parsed.level?.toString, c: parsed.level.toLowerCase(), "
            + "d: parsed.level.length } sink {} }", log().text("WARN disk full")).output.tags);
        assertRejected("filter { text { regexp \"(?<a>.*)\" } extractor { service parsed.a.b as String } sink {} }",
            "String.b");
    }

    @Test
    void parsedReadsTheLogWhenTheParserDidNotRun() throws Exception {
        final Run run = run("filter { if (log.service == \"other\") { json {} } "
            + "extractor { tag s: parsed.service } sink {} }", log());

        assertEquals(List.of("s=svc"), run.output.tags);
    }

    @Test
    void parsedFallsBackToTheLogsTypedField() throws Exception {
        // A text group reads the log's string fields only: a group is a string.
        final String skippedText = "filter { if (tag(\"k\") == \"t\") { text { regexp \"(?<x>.+)\" } } "
            + "extractor { tag s: parsed.service if (parsed.timestamp == null) { tag none: \"y\" } } sink {} }";
        assertEquals(List.of("s=svc", "none=y"), run(skippedText, log()).output.tags);

        final String skippedJson = "filter { if (tag(\"k\") == \"j\") { json {} } "
            + "extractor { tag span: parsed.traceContext.spanId } sink {} }";
        assertEquals(List.of("span=3"), run(skippedJson, log().spanId(3)).output.tags);

        // As in Groovy LAL: a name the regexp has no group of fails, a group that did not take part reads on.
        final IllegalArgumentException misspelled = assertThrows(IllegalArgumentException.class, () -> run(
            "filter { text { regexp \"(?<status>[0-9]+)\" } extractor { tag code: parsed.stauts } sink {} }",
            log().text("403")));
        assertTrue(misspelled.getMessage().contains("No group with name <stauts>"), misspelled.getMessage());
        assertEquals(List.of("s=svc"), run("filter { text { regexp \"(?<status>[0-9]+)(?<service>x)?\" } "
            + "extractor { tag s: parsed.service } sink {} }", log().text("403")).output.tags);
    }

    @Test
    void parsedReadsTheLogAsTheRuleLeftIt() throws Exception {
        assertEquals(List.of("s=canon", "actual=canon"), run("filter { extractor { service \"canon\" "
            + "tag s: parsed.service, actual: log.service } sink {} }", log()).output.tags);
        // parsed.<field> reads what log.<field> does.
        assertEquals(List.of("e=y", "z=y", "u=y"), run("filter { json {} extractor { service \"\" timestamp \"0\" "
            + "if (parsed.service == \"\") { tag e: \"y\" } if (parsed.timestamp == 0) { tag z: \"y\" } "
            + "if (parsed.endpoint == log.endpoint) { tag u: \"y\" } } sink {} }", log().json("{}")).output.tags);
        assertEquals(List.of("b={\"a\":1}"), run("filter { json {} extractor { tag b: parsed.body.json.json } sink {} }",
            log().json("{\"a\":1}")).output.tags);
    }

    @Test
    void parsedReadsATypedInputWhenItsParserDidNotParseIt() throws Exception {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(503)))
            .build();
        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setOutputType(RecordingOutput.class);
        final LalExpression expr = generator.compile("filter { json { abortOnFailure false } "
            + "extractor { tag code: parsed?.response?.responseCode?.value } sink {} }");
        final ExecutionContext ctx = new ExecutionContext().init(
            LogMetadata.builder().service("svc").layer("MESH").build(), entry);
        expr.execute(filterSpec, ctx);
        assertEquals(List.of("code=503"), ((RecordingOutput) ctx.output()).tags);
    }

    @Test
    void toJsonInputReadsItsGetters() throws Exception {
        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setInputType(PojoInput.class);
        generator.setOutputType(RecordingOutput.class);
        final LalExpression expr = generator.compile("filter { extractor { tag endpoint: parsed.name } sink {} }");
        final ExecutionContext ctx = new ExecutionContext().init(
            LogMetadata.builder().service("svc").layer("GENERAL").timestamp(1L).build(), new PojoInput("/orders"));
        expr.execute(filterSpec, ctx);
        assertEquals(List.of("endpoint=/orders"), ((RecordingOutput) ctx.output()).tags);
    }

    @Test
    void toJsonInputIsReadWhenAParserDidNotRun() throws Exception {
        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setInputType(PojoInput.class);
        generator.setOutputType(RecordingOutput.class);
        final LalExpression expr = generator.compile("filter { if (parsed.name == \"other\") { json {} } "
            + "extractor { tag path: parsed.name } sink {} }");
        final ExecutionContext ctx = new ExecutionContext().init(
            LogMetadata.builder().service("svc").layer("GENERAL").timestamp(1L).build(), new PojoInput("/orders"));
        expr.execute(filterSpec, ctx);
        assertEquals(List.of("path=/orders"), ((RecordingOutput) ctx.output()).tags);
        // A subscript calls get(int) only where the list allows it.
        final LALClassGenerator subscript = new LALClassGenerator(new ClassPool(true));
        subscript.setInputType(PojoInput.class);
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> subscript.compile("filter { extractor { tag first: parsed.payload[0] } sink {} }"));
        assertTrue(refused.getMessage().contains("ByteBuffer.get(...) is not supported"), refused.getMessage());
    }

    @Test
    void parsedHolderOffersOnlyItsMap() throws Exception {
        assertEquals(List.of("c=checkout"), run("filter { json {} extractor { tag c: parsed.getMap().get(\"code\") } "
            + "sink {} }", log().json("{\"code\":\"checkout\"}")).output.tags);
        assertRejected("filter { text { regexp \"(?<code>[A-Z]+)\" } extractor { "
            + "tag c: parsed.getMatcher().group(\"code\") } sink {} }", "Parsed.getMatcher(...) is not supported");
        assertRejected("filter { json {} extractor { tag c: parsed.getFromMetadata() } sink {} }",
            "Parsed.getFromMetadata(...) is not supported");
    }

    @Test
    void parsedGetMapHoldsTheLogsFields() throws Exception {
        assertEquals(List.of("s=svc"), run("filter { json {} extractor { "
            + "tag s: parsed.getMap().getOrDefault(\"service\", \"unknown\") } sink {} }", log().json("{}")).output.tags);
    }

    @Test
    void failedOptionalJsonParseHasNoParsedKeys() throws Exception {
        assertEquals(List.of("kind=raw"), run("filter { json { abortOnFailure false } extractor { "
            + "if (parsed.map == null) { tag kind: \"raw\" } } sink {} }", log().text("not json")).output.tags);
    }

    @Test
    void unmatchedOptionalGroupIsNeverAStringOfANonStringField() throws Exception {
        // The log's timestamp is not a string, so the group the match did not set reads null.
        assertTrue(run("filter { text { regexp \"(?<timestamp>[0-9]+)?WARN\" } extractor { "
            + "if (parsed.timestamp == null) { tag t: \"y\" } } sink {} }",
            log().text("WARN")).output.tags.contains("t=y"));
    }

    @Test
    void textBranchKeepsItsStringGroupBesideAJsonBranch() throws Exception {
        final String dsl = "filter { if (tag(\"k\") == \"t\") { text { regexp \"(?<level>[A-Z]+)\" } "
            + "extractor { tag l: parsed.level.toLowerCase() } } else { json {} } "
            + "extractor { tag m: parsed.level.toLowerCase() } sink {} }";
        assertEquals(List.of("l=warn", "m=warn"), run(dsl, log().tag("k", "t").text("WARN x")).output.tags);
        assertEquals(List.of("m=info"), run(dsl, log().json("{\"level\":\"INFO\"}")).output.tags);
    }

    @Test
    void methodOnAMixedParserValueIsAStringOrFails() throws Exception {
        for (final String parsers : List.of(
                "if (tag(\"k\") == \"t\") { text { regexp \"(?<code>[0-9]+)\" } } else { json {} }",
                "if (tag(\"k\") != \"t\") { json {} } else { text { regexp \"(?<code>[0-9]+)\" } }")) {
            // An Object method takes the value as it is, a text group or a json number.
            final String any = "filter { " + parsers + " extractor { tag s: parsed.code.toString() } sink {} }";
            assertEquals(List.of("s=500"), run(any, log().tag("k", "t").text("500")).output.tags);
            assertEquals(List.of("s=500"), run(any, log().json("{\"code\":500}")).output.tags);
            // A string method: a json number is not the string it was compiled for, so the log fails.
            final String string = "filter { " + parsers + " extractor { tag s: parsed.code.startsWith(\"5\") } sink {} }";
            assertEquals(List.of("s=true"), run(string, log().tag("k", "t").text("500")).output.tags);
            assertThrows(ClassCastException.class, () -> run(string, log().json("{\"code\":500}")));
        }
    }

    @Test
    void textGroupIsAStringAfterItsBranchRejoinsAJsonOne() throws Exception {
        final String dsl = "filter { if (tag(\"kind\") == \"text\") { text { regexp \"(?<msg>[A-Z]+) (?<term>[A-Z]+)\" } } "
            + "else { json {} } extractor { def msg = parsed.msg as String "
            + "tag o: \"offset=\" + msg.indexOf(parsed.term), l: (parsed.msg).toLowerCase() } sink {} }";
        assertEquals(List.of("o=offset=1", "l=xerror"), run(dsl, log().tag("kind", "text").text("XERROR ERR"))
            .output.tags);
        assertEquals(List.of("o=offset=1", "l=xerror"), run(dsl, log().tag("kind", "json")
            .json("{\"msg\":\"XERROR\",\"term\":\"ERR\"}")).output.tags);
        assertThrows(ClassCastException.class, () -> run(dsl, log().tag("kind", "json")
            .json("{\"msg\":\"XERROR\",\"term\":1}")));
    }

    @Test
    void textGroupIsAStringForMethodsAndArguments() throws Exception {
        final String dsl = "filter { text { regexp \"(?<msg>\\w+) (?<term>\\w+)\" } extractor { "
            + "def msg = parsed.msg as String tag o: \"offset=\" + msg.indexOf(parsed.term), "
            + "l: (parsed.msg).toLowerCase() if (msg.equals(parsed.msg)) { tag eq: \"y\" } } sink {} }";
        assertEquals(List.of("o=offset=1", "l=xerror", "eq=y"), run(dsl, log().text("XERROR ERR")).output.tags);
    }

    @Test
    void parenthesizedTextGroupArgument() throws Exception {
        assertEquals(List.of("o=offset=1"), run("filter { if (tag(\"kind\") == \"text\") { text { regexp "
            + "\"(?<msg>[A-Z]+) (?<term>[A-Z]+)\" } } else { json {} } extractor { def msg = parsed.msg as String "
            + "tag o: \"offset=\" + msg.indexOf((parsed.term)) } sink {} }", log().tag("kind", "text")
            .text("XERROR ERR")).output.tags);
    }

    @Test
    void stringEqualsOnATextGroupAfterBranchesRejoin() throws Exception {
        final String dsl = "filter { if (tag(\"fmt\") == \"t\") { text { regexp \"(?<level>[A-Z]+)\" } } else { json {} } "
            + "extractor { if (tag(\"fmt\") == \"t\" && parsed.level.equals(\"WARN\")) { tag hit: \"y\" } } sink {} }";
        assertEquals(List.of("hit=y"), run(dsl, log().tag("fmt", "t").text("WARN")).output.tags);
        assertTrue(run(dsl, log().tag("fmt", "t").text("INFO")).output.tags.isEmpty());
        assertTrue(run(dsl, log().tag("fmt", "j").json("{\"level\":\"WARN\"}")).output.tags.isEmpty());
    }

    @Test
    void defOfATextGroupTakesStringMethodsAfterBranchesRejoin() throws Exception {
        final String dsl = "filter { if (tag(\"fmt\") == \"t\") { text { regexp \"(?<level>[A-Z]+)\" } } else { json {} } "
            + "extractor { def level = parsed.level if (tag(\"fmt\") == \"t\" && level.equals(\"WARN\")) { tag hit: \"y\" } } "
            + "sink {} }";
        assertEquals(List.of("hit=y"), run(dsl, log().tag("fmt", "t").text("WARN")).output.tags);
        assertTrue(run(dsl, log().tag("fmt", "t").text("INFO")).output.tags.isEmpty());
        assertTrue(run(dsl, log().tag("fmt", "j").json("{\"level\":5}")).output.tags.isEmpty());
    }

    // ==================== log.* and standard fields ====================

    @Test
    void standardFieldsSetWhatLogReadsAndTheTraffic() throws Exception {
        final List<ISource> traffic = recordTraffic();
        final Run run = run("filter { extractor { service \"svc2\" instance \"inst2\" endpoint \"/ep\" "
            + "traceId \"t1\" segmentId \"s1\" spanId \"3\" "
            + "tag s: log.service, i: log.serviceInstance, e: log.endpoint, t: log.traceContext.traceId, "
            + "g: log.traceContext.traceSegmentId, p: log.traceContext.spanId } sink {} }", log());

        assertEquals(List.of("s=svc2", "i=inst2", "e=/ep", "t=t1", "g=s1", "p=3"), run.output.tags);
        assertEquals(List.of("service svc2", "instance inst2", "endpoint /ep"), describe(traffic));
    }

    @Test
    void standardFieldGivenNullKeepsTheLogsValue() throws Exception {
        final Run run = run("filter { json {} extractor { service parsed.missing as String tag s: log.service } "
            + "sink {} }", log().json("{}"));

        assertEquals(List.of("s=svc"), run.output.tags);
    }

    @Test
    void standardFieldIsWhatEveryReadOfItSees() throws Exception {
        assertEquals(List.of("a=canon", "b=canon", "c=canon"), run("filter { json {} extractor { service \"canon\" "
            + "tag a: log.service, b: log.getService(), c: parsed.service } sink {} }", log().json("{}")).output.tags);
        assertEquals(List.of("c=body"), run("filter { json {} extractor { service \"canon\" tag c: parsed.service } "
            + "sink {} }", log().json("{\"service\":\"body\"}")).output.tags);
    }

    @Test
    void standardFieldNullLeavesBothCopies() throws Exception {
        final Run layer = run("filter { json {} extractor { def m = parsed.m as java.util.Map "
            + "layer m.get(\"layer\") as String } sink {} }", log().json("{\"m\":{\"layer\":\"GENERAL\"}}"));
        assertEquals("GENERAL", layer.ctx.layer());
        assertEquals(1, saved);

        final Run service = run("filter { json {} extractor { service \"known\" service parsed.missing as String } "
            + "sink {} }", log().json("{}"));
        assertEquals("known", service.ctx.metadata().getService());
        assertEquals("known", serviceOf(service.output));
    }

    @Test
    void shippedRulesBuildTrafficFromTheirFields() throws Exception {
        final List<ISource> traffic = recordTraffic();
        run(shippedRule("miniprogram.yaml", "miniprogram-errors-wechat"), log()
            .tag("exception.type", "TypeError").tag("miniprogram.page.path", "pages/index")
            .attribute("service.instance.id", "device-1"));
        assertEquals(List.of("service svc", "instance device-1", "endpoint pages/index"), describe(traffic));

        traffic.clear();
        run(shippedRule("ios-metrickit.yaml", "ios-metrickit-diagnostics"), log()
            .tag("name", "metrickit.diagnostic.crash").attribute("os.name", "iOS").attribute("service.version", "2.1.0"));
        assertEquals(List.of("service svc", "instance 2.1.0"), describe(traffic));

        traffic.clear();
        run(shippedRule("mysql-slowsql.yaml", "mysql-slowsql"), DatabaseSlowStatementBuilder.class, log().json(
            "{\"layer\":\"MYSQL\",\"service\":\"mysql::db\",\"time\":\"1\",\"query_time\":1}"));
        assertEquals(List.of("service mysql::db"), describe(traffic));
    }

    @Test
    void timestampKeepsTheLogsWhenTheValueIsNotATimestamp() throws Exception {
        final String number = "filter { json {} extractor { timestamp parsed.t as String } sink {} }";
        assertEquals(1L, run(number, log().json("{}")).output.getTimestamp());
        assertEquals(1L, run(number, log().json("{\"t\":\"abc\"}")).output.getTimestamp());
        assertEquals(123L, run(number, log().json("{\"t\":\"123\"}")).output.getTimestamp());

        final String pattern = "filter { json {} extractor { timestamp parsed.t as String, \"yyyy-MM-dd\" } sink {} }";
        assertEquals(1L, run(pattern, log().json("{\"t\":\"not a date\"}")).output.getTimestamp());
    }

    @Test
    void spanIdTakesAnIntegerCastAndAPaddedNumber() throws Exception {
        assertEquals(List.of("p=3"), run("filter { json {} extractor { spanId parsed.s as Integer "
            + "tag p: log.traceContext.spanId } sink {} }", log().json("{\"s\":\" 3 \"}")).output.tags);
        assertEquals(List.of("p=4"), run("filter { json {} extractor { spanId parsed.s "
            + "tag p: log.traceContext.spanId } sink {} }", log().json("{\"s\":\" 4 \"}")).output.tags);
    }

    @Test
    void logTraceContextKeepsItsProtobufApi() throws Exception {
        assertEquals(List.of("e=empty"), run("filter { extractor { if (log.getTraceContext().getTraceId()"
            + ".isEmpty()) { tag e: \"empty\" } } sink {} }", log()).output.tags);
        assertEquals(List.of("e=false"), run("filter { extractor { traceId \"abc\" "
            + "tag e: log.getTraceContext().getTraceId().isEmpty() } sink {} }", log()).output.tags);
        // log.traceContext stays the metadata's trace context, whether read directly or through a def.
        assertEquals(List.of("d=abc", "t=abc"), run("filter { extractor { traceId \"abc\" def tc = log.traceContext "
            + "tag d: log.traceContext.traceId, t: tc.traceId } sink {} }", log()).output.tags);
    }

    @Test
    void logPresenceGettersSeeTheRulesWrites() throws Exception {
        assertEquals(List.of("category=foo", "both=y"), run("filter { extractor { tag category: \"foo\" "
            + "if (log.hasTags() && !log.getTags().getDataList().isEmpty()) { tag both: \"y\" } } sink {} }", log())
            .output.tags);
        assertEquals(List.of("both=y"), run("filter { extractor { traceId \"newtrace\" "
            + "if (log.hasTraceContext() && log.getTraceContext().getTraceId() == \"newtrace\") { tag both: \"y\" } } "
            + "sink {} }", log()).output.tags);
    }

    @Test
    void logGettersReadTheLogAsTheRuleLeftIt() throws Exception {
        assertEquals(List.of("t=y"), run("filter { extractor { service \"canonical\" "
            + "if (log.toString().contains(\"canonical\")) { tag t: \"y\" } } sink {} }", log()).output.tags);
        assertEquals(List.of("p=y"), run("filter { extractor { spanId \"0\" if (log.hasTraceContext()) { tag p: \"y\" } } "
            + "sink {} }", log()).output.tags);
    }

    @Test
    void protobufOrBuilderAndIndexedGettersRead() throws Exception {
        assertEquals(List.of("j={\"a\":1}", "v=x"), run("filter { json {} extractor { "
            + "tag j: log.getBodyOrBuilder().getJson().getJson(), v: log.getTags().getDataOrBuilder(0).getValue() } "
            + "sink {} }", log().tag("k", "x").json("{\"a\":1}")).output.tags);
    }

    // ==================== Tags ====================

    @Test
    void tagMapCastAppliesToItsOwnEntry() throws Exception {
        final Run run = run("filter { json {} extractor { tag a: parsed.first, b: parsed.second as Integer } "
            + "sink {} }", log().json("{\"first\":\"009\",\"second\":\"007\"}"));

        assertEquals(List.of("a=009", "b=7"), run.output.tags);
    }

    @Test
    void repeatedTagKeyKeepsItsLastValue() throws Exception {
        assertEquals(List.of("a=2"), run("filter { extractor { tag a: \"1\", a: \"2\" } sink {} }", log()).output.tags);
    }

    @Test
    void tagReadsTheTagsTheRuleAdded() throws Exception {
        final Run run = run("filter { extractor { tag route: \"checkout\" tag copy: tag(\"route\") } "
            + "sink { if (tag(\"route\") == \"checkout\") { dropper {} } } }", log());
        assertEquals(List.of("route=checkout", "copy=checkout"), run.output.tags);
        assertEquals(0, saved);
    }

    @Test
    void tagStatementReadsTagsBeforeItAndLogTagsAfterIt() throws Exception {
        assertEquals(List.of("city=Paris", "count=1"), run("filter { extractor { "
            + "tag city: \"Paris\", prior: tag(\"city\") tag count: log.tags.dataCount } sink {} }", log())
            .output.tags);
        assertRejected("filter { extractor { tag k: prased.a, k: \"ok\" } sink {} }", "Unknown name 'prased'");
    }

    @Test
    void tagValueIsWrittenAsGroovyLALWroteIt() throws Exception {
        assertEquals(List.of("l=a,b"), run("filter { json {} extractor { tag l: parsed.l, w: parsed.w } sink {} }",
            log().json("{\"l\":[\"a\",null,\"b\"],\"w\":\"  \"}")).output.tags);
    }

    @Test
    void blankTagKeyIsNotAdded() throws Exception {
        assertEquals(List.of("n=0"), run("filter { extractor { tag \"\": \"oops\" tag n: log.tags.dataCount } sink {} }",
            log()).output.tags);
    }

    @Test
    void tagOfAnEmptyListIsNotCountedAsTheLogDropsIt() throws Exception {
        assertEquals(List.of("counted=0"), run("filter { json {} extractor { tag hosts: parsed.hosts "
            + "tag counted: log.tags.dataCount } sink {} }", log().json("{\"hosts\":[]}")).output.tags);
    }

    // ==================== Defs ====================

    @Test
    void defIsScopedToItsIfBranch() throws Exception {
        assertRejected("filter { extractor { if (tag(\"a\") == \"x\") { def y = \"1\" } tag b: y } sink {} }",
            "def 'y' is used outside the block that declares it");

        // Each branch's def is its own variable, whatever its type.
        final String dsl = "filter { extractor { if (tag(\"a\") == \"x\") { def y = tag(\"a\").length() tag n: y } "
            + "else { def y = tag(\"a\") tag s: y.toUpperCase() } } sink {} }";
        assertEquals(List.of("n=1"), run(dsl, log().tag("a", "x")).output.tags);
        assertEquals(List.of("s=Q"), run(dsl, log().tag("a", "q")).output.tags);
    }

    @Test
    void filterDefIsVisibleInsideItsExtractorAndSink() throws Exception {
        final String dsl = "filter { def tenant = tag(\"tenant\").trim() "
            + "if (tenant == \"\") { abort {} } "
            + "extractor { tag tenant: tenant } "
            + "sink { sampler { rateLimit(\"${tenant}\") { rpm 100 } } } }";

        final Run run = run(dsl, log().tag("tenant", " acme "));
        assertEquals(List.of("tenant=acme"), run.output.tags);
        assertEquals(List.of("acme"), samplerIds());
        assertTrue(run(dsl, log()).ctx.shouldAbort());
    }

    @Test
    void redeclaringAVisibleDefIsRefused() {
        assertRejected("filter { extractor { def origin = \"outer\" if (tag(\"k\") == \"v\") { def origin = \"inner\" } "
            + "tag after: origin } sink {} }", "def 'origin' is already declared");
    }

    @Test
    void defOfAnotherTypeDoesNotTakeAnotherDefsVariable() throws Exception {
        final String dsl = "filter { extractor { def status_2 = \"keep\" "
            + "if (tag(\"a\") == \"x\") { def status = 1 tag s: status } else { def status = \"s\" tag s: status } "
            + "tag k: status_2 } sink {} }";

        assertEquals(List.of("s=1", "k=keep"), run(dsl, log().tag("a", "x")).output.tags);
        assertEquals(List.of("s=s", "k=keep"), run(dsl, log().tag("a", "y")).output.tags);
    }

    @Test
    void classCastInADefConvertsAsGroovysAs() throws Exception {
        assertEquals(List.of("n=2", "b=123"), run("filter { json {} extractor { "
            + "def ids = parsed.ids as java.util.Set def b = parsed.big as java.math.BigInteger "
            + "tag n: ids.size(), b: b } sink {} }", log().json("{\"ids\":[1,1,2],\"big\":\"123\"}")).output.tags);
        assertThrows(ClassCastException.class, () -> run("filter { json {} extractor { "
            + "def m = parsed.s as java.util.Map tag m: m } sink {} }", log().json("{\"s\":\"x\"}")));
    }

    @Test
    void enumClassCastConvertsAString() throws Exception {
        assertEquals(List.of("d=SERVER"), run("filter { json {} extractor { "
            + "def dp = parsed.d as org.apache.skywalking.oap.server.core.source.DetectPoint tag d: dp } sink {} }",
            log().json("{\"d\":\"SERVER\"}")).output.tags);
        // Only an enum the output type takes.
        assertRejected("filter { json {} extractor { def u = parsed.u as java.util.concurrent.TimeUnit tag u: u } "
            + "sink {} }", "A def cannot be cast as java.util.concurrent.TimeUnit");
    }

    @Test
    void arrayDefAndMethodResultArithmeticCompile() throws Exception {
        assertEquals(List.of("f=a", "i=2"), run("filter { extractor { def parts = tag(\"p\").split(\"/\") "
            + "def s = tag(\"s\") def k = tag(\"k\") tag f: parts[0], i: s.indexOf(k) * 2 } sink {} }",
            log().tag("p", "a/b").tag("s", "ab").tag("k", "b")).output.tags.subList(0, 2));
    }

    // ==================== Casts ====================

    @Test
    void misspelledOrClassCastIsRejected() {
        assertRejected("filter { json {} extractor { service parsed.a as string } sink {} }", "did you mean 'as String'");
        assertRejected("filter { json {} extractor { tag a: parsed.a as java.util.Map } sink {} }",
            "only supported on def");
    }

    @Test
    void paddedNumberStringConverts() throws Exception {
        assertEquals(42L, run("filter { json {} extractor { count parsed.n as Long } sink {} }",
            log().json("{\"n\":\" 42 \"}")).output.count);
    }

    @Test
    void primitiveCastConvertsAndFailsOnANull() throws Exception {
        assertEquals(List.of("l=42", "b=false"), run("filter { json {} extractor { tag l: parsed.q as long, "
            + "b: parsed.missing as boolean } sink {} }", log().json("{\"q\":\" 42 \"}")).output.tags);
        assertEquals(42L, run("filter { json {} extractor { count parsed.q as long } sink {} }",
            log().json("{\"q\":\"42\"}")).output.count);
        assertThrows(IllegalArgumentException.class, () -> run(
            "filter { json {} extractor { tag l: parsed.missing as long } sink {} }", log().json("{}")));
    }

    @Test
    void asBooleanConvertsABooleanTheTextTrueOrFalseAndANumberByZero() throws Exception {
        final Run run = run("filter { json {} extractor { tag f: parsed.f as Boolean, t: parsed.t as Boolean, "
            + "e: parsed.e as Boolean, z: parsed.z as Boolean, o: parsed.o as Boolean, b: parsed.b as Boolean, "
            + "s: parsed.s as Boolean, n: parsed.n as Boolean } sink {} }", log().json(
            "{\"f\":\"false\",\"t\":\" TRUE \",\"e\":\"\",\"z\":0,\"o\":2,\"b\":true,\"s\":\"1\",\"n\":\"0\"}"));
        assertEquals(List.of("f=false", "t=true", "e=false", "z=false", "o=true", "b=true", "s=true", "n=false"),
            run.output.tags);

        assertFalse(aborts("parsed.f as Boolean", log().json("{\"f\":\"false\"}")));
        assertTrue(aborts("parsed.f as Boolean", log().json("{\"f\":\"true\"}")));
    }

    @Test
    void asBooleanFailsTheLogOnAValueWithNoBooleanMeaning() {
        for (final String json : new String[] {"{\"f\":\"yes\"}", "{\"f\":\"on\"}", "{\"f\":[true]}"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> run("filter { json {} extractor { tag f: parsed.f as Boolean } sink {} }", log().json(json)));
            assertTrue(e.getMessage().contains("cannot be converted to boolean"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> run(
            "filter { extractor { def s = tag(\"s\") if (s.charAt(0) as Boolean) { tag t: \"y\" } } sink {} }",
            log().tag("s", "\u0000x")), "a character is no boolean");
    }

    @Test
    void blankTextFailsANumericCastHeldOrCompared() throws Exception {
        // a missing tag is "", which no numeric cast reads, held or compared
        assertThrows(Exception.class, () -> aborts("(parsed.n as Integer) == null", log().json("{\"n\":\"  \"}")));
        assertThrows(Exception.class, () -> run("filter { json {} extractor { def n = tag(\"missing\") as Long\n"
            + " if (n == null) { abort {} } } sink {} }", log().json("{}")));
        assertTrue(aborts("(parsed.n as Integer) == null", log().json("{}")), "a missing value stays null");
    }

    @Test
    void doubleSetterKeepsTheFraction() throws Exception {
        final Run run = run("filter { json {} extractor { ratio parsed.r } sink {} }", log().json("{\"r\":1.75}"));

        assertEquals(1.75, run.output.ratio);
    }

    @Test
    void setterTakesTheRulesCastBeforeWidening() throws Exception {
        assertEquals(1.0, run("filter { json {} extractor { ratio parsed.n as Integer } sink {} }",
            log().json("{\"n\":1.75}")).output.ratio);

        // Any cast converts first, then the parameter's conversion.
        assertEquals(1L, run("filter { json {} extractor { count parsed.n as Double } sink {} }",
            log().json("{\"n\":1.75}")).output.count);
        assertEquals(3L, run("filter { json {} extractor { count parsed.n as String } sink {} }",
            log().json("{\"n\":\" 3 \"}")).output.count);
        assertEquals("1", run("filter { json {} extractor { service parsed.n as Integer } sink {} }",
            log().json("{\"n\":1.75}")).ctx.metadata().getService());
    }

    @Test
    void castOfABoxedSafeNavigationResultConverts() throws Exception {
        final String dsl = "filter { json {} extractor { def v = toJson(parsed.m) "
            + "if (v?.get(\"latency\")?.getAsDouble() as Integer == 1.0) { tag hit: \"y\" } } sink {} }";

        assertEquals(List.of("hit=y"), run(dsl, log().json("{\"m\":{\"latency\":1.75}}")).output.tags);
        assertTrue(run(dsl, log().json("{\"m\":{}}")).output.tags.isEmpty());
    }

    @Test
    void parenthesizedStringCastIsAString() throws Exception {
        assertEquals(List.of("hit=y"), run("filter { json {} extractor { def code = parsed.code as Integer "
            + "def msg = parsed.msg as String if (msg.startsWith((code as String))) { tag hit: \"y\" } } sink {} }",
            log().json("{\"code\":500,\"msg\":\"500 error\"}")).output.tags);
    }

    @Test
    void characterIsNotCastAsANumber() {
        assertRejected("filter { json {} extractor { def code = parsed.code as String "
            + "tag head: code.charAt(0) as Integer } sink {} }", "A character cannot be cast as a number");
    }

    @Test
    void characterCastInAMetricIsRefused() {
        final String code = "filter { json {} extractor { def code = parsed.code as String metrics { name \"m\" ";
        assertRejected(code + "value code.charAt(0) as Integer } } sink {} }", "A character cannot be cast");
        assertRejected(code + "timestamp code.charAt(0) as Long value 1 } } sink {} }", "A character cannot be cast");
    }

    @Test
    void booleanCastAsANumberIsRefused() {
        assertRejected("filter { json {} extractor { tag f: (parsed.flag as Boolean) as Integer } sink {} }",
            "A boolean cannot be cast as a number");
        assertRejected("filter { json {} extractor { tag f: true as Integer } sink {} }",
            "A boolean cannot be cast as a number");
    }

    @Test
    void booleanOrCharacterIsNotCastAsANumberInAComparison() {
        final String status = "filter { json {} extractor { def status = parsed.status as String if (";
        assertRejected(status + "status.startsWith(\"5\") as Integer == 1) { tag t: \"y\" } } sink {} }",
            "A boolean cannot be cast as a number");
        assertRejected(status + "1 == status.startsWith(\"5\") as Integer) { tag t: \"y\" } } sink {} }",
            "A boolean cannot be cast as a number");
        assertRejected(status + "status.charAt(0) as Integer == 53) { tag t: \"y\" } } sink {} }",
            "A character cannot be cast as a number");
    }

    // ==================== Equality ====================

    @Test
    void castOnEitherSideOfEqualsConverts() throws Exception {
        final String rhsCast = "filter { json {} if (tag(\"k\") == parsed.n as String) { abort {} } sink {} }";
        assertTrue(run(rhsCast, log().tag("k", "5").json("{\"n\":5}")).ctx.shouldAbort());

        // A string equals a number as the number it holds.
        final String lhsCast = "filter { if (tag(\"k\") as Integer == tag(\"j\")) { abort {} } sink {} }";
        assertTrue(run(lhsCast, log().tag("k", "5").tag("j", " 5")).ctx.shouldAbort());
        assertFalse(run(lhsCast, log().tag("k", "5").tag("j", "five")).ctx.shouldAbort());
    }

    @Test
    void equalityComparesNumbersByValue() throws Exception {
        assertTrue(aborts("parsed.status as String == 200", log().json("{\"status\":200}")));
        assertFalse(aborts("parsed.n == 1", log().json("{\"n\":1.9}")));
        assertTrue(aborts("parsed.a == parsed.b as Long", log().json("{\"a\":3,\"b\":3}")));
        assertTrue(aborts("parsed.a == 1.5", log().json("{\"a\":1.5}")));
    }

    @Test
    void stringEqualsANumberAsTheNumberItHolds() throws Exception {
        assertTrue(aborts("parsed.n == 3", log().json("{\"n\":\" 3 \"}")));
        assertTrue(aborts("tag(\"a\") == 200", log().tag("a", "200").json("{}")));
        assertTrue(aborts("parsed.n == \"3.0\"", log().json("{\"n\":3}")));
        assertTrue(aborts("parsed.n as Integer == \"3\"", log().json("{\"n\":3}")));
        assertTrue(aborts("parsed.n != \"4\"", log().json("{\"n\":3}")));
        // One holding no number is unequal, without failing; two strings compare as text.
        assertFalse(aborts("parsed.n == 3", log().json("{\"n\":\"abc\"}")));
        assertTrue(aborts("tag(\"a\") != 3", log().tag("a", "").json("{}")));
        assertFalse(aborts("parsed.n == \"3.0\"", log().json("{\"n\":\"3\"}")));
        assertFalse(aborts("tag(\"a\") == \"3.0\"", log().tag("a", "3").json("{}")));
    }

    @Test
    void characterEqualsAsItsOneCharacterString() throws Exception {
        final Input digit = log().tag("s", "5x").tag("one", "1").json("{}");
        assertTrue(aborts("tag(\"one\").charAt(0) == 1", digit));
        assertTrue(aborts("tag(\"s\").charAt(0) == \"5\"", digit));
        assertTrue(aborts("tag(\"s\").charAt(1) != 120", digit));
        assertTrue(aborts("parsed.n == tag(\"one\").charAt(0)", log().tag("one", "1").json("{\"n\":1}")));
    }

    @Test
    void equalityComparesCharactersAsStringsAndListsAndMapsByValue() throws Exception {
        assertTrue(aborts("\"ERROR\".charAt(0) == \"E\"", log().json("{}")));
        assertTrue(aborts("parsed.a == parsed.b", log().json("{\"a\":[1],\"b\":[1.0]}")));
        assertTrue(aborts("parsed.a == parsed.b", log().json("{\"a\":{\"k\":[1]},\"b\":{\"k\":[1.0]}}")));
        // Groovy compared a one-character string with a number by its code ("A" == 65, "65" != 65).
        assertFalse(aborts("parsed.s == 65", log().json("{\"s\":\"A\"}")));
        assertFalse(aborts("parsed.n == \"A\"", log().json("{\"n\":65}")));
        assertTrue(aborts("parsed.n == \"65\"", log().json("{\"n\":65}")));
    }

    @Test
    void aJsonValueComparesByWhatItHolds() throws Exception {
        final String dsl = "filter { json {} extractor { def m = toJson(parsed.m) "
            + "if (m.get(\"env\") == \"prod\") { tag env: \"y\" } "
            + "if (m.get(\"n\") == 7) { tag n: \"y\" } "
            + "if (m == parsed.m) { tag same: \"y\" } } sink {} }";
        assertEquals(List.of("env=y", "n=y", "same=y"),
            run(dsl, log().json("{\"m\":{\"env\":\"prod\",\"n\":7.0}}")).output.tags);
        assertTrue(run(dsl, log().json("{\"m\":{\"env\":\"dev\",\"n\":8}}")).output.tags.contains("same=y"));
        assertEquals(1, run(dsl, log().json("{\"m\":{\"env\":\"dev\",\"n\":8}}")).output.tags.size());
    }

    // ==================== Ordering and null ====================

    @Test
    void orderingComparesNumbersByValue() throws Exception {
        assertTrue(aborts("parsed.n > 1", log().json("{\"n\":1.9}")));
    }

    @Test
    void stringOrdersAgainstANumberAsTheNumberItHolds() throws Exception {
        // Groovy compared a one-character string by its code ("3" < 5 was false).
        assertTrue(aborts("tag(\"a\") < 5", log().tag("a", " 3 ").json("{}")));
        assertTrue(aborts("parsed.n > 5", log().json("{\"n\":\"10\"}")));
        assertTrue(aborts("5.5d > parsed.n", log().json("{\"n\":\"3.25\"}")));
        assertTrue(aborts("parsed.n as String < 5", log().json("{\"n\":3}")));
        assertTrue(aborts("\"3\" < 5", log().json("{}")));
        // A string holding no number fails: the compile for a literal, the log otherwise.
        assertRejected("filter { if (\"abc\" < 5) { abort {} } sink {} }", "is not a number");
        assertRejected("filter { if (5 > \"abc\") { abort {} } sink {} }", "is not a number");
        final IllegalArgumentException runtime = assertThrows(IllegalArgumentException.class,
            () -> aborts("tag(\"a\") > 5", log().tag("a", "abc").json("{}")));
        assertTrue(runtime.getMessage().contains("reads as no number"), runtime.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> aborts("parsed.latency > 5.5d", log().json("{\"latency\":\"NaN\"}")));
    }

    @Test
    void characterOrdersAsItsOneCharacterString() throws Exception {
        final Input digit = log().tag("s", "5x").json("{}");
        assertTrue(aborts("tag(\"s\").charAt(0) > 4", digit));
        assertTrue(aborts("4 < tag(\"s\").charAt(0)", digit));
        assertTrue(aborts("tag(\"s\").charAt(0) < \"10\"", digit));
        assertTrue(aborts("tag(\"s\").charAt(1) > \"10\"", digit));
        // One holding no number fails an ordering against a number.
        final IllegalArgumentException runtime = assertThrows(IllegalArgumentException.class,
            () -> aborts("tag(\"s\").charAt(1) > 4", digit));
        assertTrue(runtime.getMessage().contains("reads as no number"), runtime.getMessage());
    }

    @Test
    void twoStringsOrderAsNumbersWhenBothHoldOne() throws Exception {
        final Input numbers = log().tag("a", "10").tag("b", "2").json("{\"used\":\"10\",\"limit\":\"2\"}");
        assertTrue(aborts("parsed.used > parsed.limit", numbers));
        assertTrue(aborts("parsed.used as String > parsed.limit as String", numbers));
        assertTrue(aborts("tag(\"a\") > tag(\"b\")", numbers));
        assertTrue(aborts("\"10\" > \"9\"", numbers));
        // Otherwise as text.
        assertTrue(aborts("parsed.used < parsed.limit", log().json("{\"used\":\"a\",\"limit\":\"b\"}")));
        assertTrue(aborts("tag(\"a\") < tag(\"b\")", log().tag("a", "10").tag("b", "abc").json("{}")));
    }

    /**
     * A number known when compiled, grouped, a log field (read or called), a number def, grouped or not, or a
     * quotient, ordered against a string literal holding no number.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "filter { json {} if ((\"abc\") < 5) { abort {} } sink {} }",
        "filter { json {} if (log.timestamp > \"unknown\") { abort {} } sink {} }",
        "filter { json {} if (log.getTimestamp() > \"unknown\") { abort {} } sink {} }",
        "filter { json {} extractor { def n = parsed.n as java.math.BigInteger "
            + "if (n > \"unknown\") { tag t: \"y\" } } sink {} }",
        "filter { json {} extractor { def n = parsed.n as java.math.BigInteger "
            + "if ((n) > \"unknown\") { tag t: \"y\" } } sink {} }",
        "filter { json {} extractor { def q = 7 / 2 if (q > \"unknown\") { tag t: \"y\" } } sink {} }"
    })
    void orderingAgainstAStringLiteralHoldingNoNumberIsRefused(final String dsl) {
        assertRejected(dsl, "is not a number");
    }

    @Test
    void aMapHasNoOrderAgainstItselfNeither() throws Exception {
        assertThrows(Exception.class, () -> aborts("parsed.m <= parsed.m", log().json("{\"m\":{\"a\":1}}")));
    }

    @Test
    void nullComparesAsInGroovy() throws Exception {
        final Input missing = log().json("{}");
        // A null equals no number and orders below every one.
        assertFalse(aborts("parsed.n as Integer == 0", missing));
        assertTrue(aborts("parsed.n as Integer != 0", missing));
        assertTrue(aborts("parsed.n as Integer < 1", missing));
        assertTrue(aborts("parsed.n as Integer <= 0", missing));
        assertFalse(aborts("parsed.n as Integer > 0", missing));
        assertFalse(aborts("0 < parsed.n as Integer", missing));
        assertTrue(aborts("0 > parsed.n as Integer", missing));
        assertFalse(aborts("parsed.n == 0", missing));
        assertTrue(aborts("parsed.n as Integer == parsed.m as Integer", missing));
        assertFalse(aborts("parsed.n as Integer < parsed.m as Integer", missing));
        assertFalse(aborts("(parsed.n as Integer) == 0", missing));
        assertFalse(aborts("(parsed.n as Integer)", missing));

        assertTrue(aborts("parsed.n as Integer == 0", log().json("{\"n\":\"0\"}")));
    }

    @Test
    void orderingAgainstNullFollowsGroovy() throws Exception {
        assertTrue(aborts("parsed.a as Integer > null", log().json("{\"a\":1}")));
        assertFalse(aborts("parsed.a as Integer > null", log().json("{}")));
        assertTrue(aborts("parsed.a as Integer >= null", log().json("{}")));
    }

    @Test
    void bothSidesConvertBeforeNullOrdering() {
        assertThrows(NumberFormatException.class,
            () -> aborts("parsed.min as Integer < parsed.max as Integer", log().json("{\"max\":\"oops\"}")));
    }

    @Test
    void explicitPrimitiveCastFailsOnANullInAComparison() {
        assertThrows(IllegalArgumentException.class,
            () -> aborts("(parsed.x as Integer) as int < 0", log().json("{}")));
    }

    @Test
    void nullCastIntoArithmeticOrAPrimitiveArgumentThrows() {
        final IllegalArgumentException arithmetic = assertThrows(IllegalArgumentException.class,
            () -> run("filter { json {} extractor { tag t: (parsed.n as Integer) + 1 } sink {} }", log().json("{}")));
        assertTrue(arithmetic.getMessage().contains("null cannot be converted to int"), arithmetic.getMessage());

        assertThrows(IllegalArgumentException.class,
            () -> run("filter { json {} extractor { tag t: \"abc\".substring(parsed.n as Integer) } sink {} }",
                log().json("{}")));
        assertThrows(IllegalArgumentException.class,
            () -> run("filter { json {} extractor { metrics { name \"m\" value parsed.n as Double } } }",
                log().json("{}")));
    }

    @Test
    void nullCastKeptAsAnObjectStaysNull() throws Exception {
        final Run run = run("filter { json {} extractor { def n = parsed.n as Integer "
            + "tag t: parsed.n as Integer, b: parsed.n as Boolean "
            + "if (n == null) { tag isNull: \"yes\" } } sink {} }", log().json("{}"));

        // A tag of a null is not added, as Groovy LAL's tag statement skipped it.
        assertEquals(List.of("isNull=yes"), run.output.tags);
    }

    @Test
    void primitiveOrEnumSetterIsNotCalledWithNull() throws Exception {
        final String dsl = "filter { json {} extractor { count parsed.n as Long kind parsed.k } sink {} }";

        final Run missing = run(dsl, log().json("{}"));
        assertEquals(-1L, missing.output.count);
        assertNull(missing.output.kind);

        final Run present = run(dsl, log().json("{\"n\":\"5\",\"k\":\"big\"}"));
        assertEquals(5L, present.output.count);
        assertEquals(Kind.BIG, present.output.kind);
    }

    @Test
    void nanIsFalseAndComparesFalseButInNotEquals() throws Exception {
        assertFalse(aborts("parsed.x as Double", log().json("{\"x\":\"NaN\"}")));
        assertFalse(aborts("parsed.x as Double > 1", log().json("{\"x\":\"NaN\"}")));
        assertTrue(aborts("parsed.x as Double != parsed.y as Double", log().json("{\"x\":\"NaN\",\"y\":1}")));
        assertTrue(aborts("parsed.x as Double != parsed.y as Double", log().json("{\"x\":\"NaN\",\"y\":\"NaN\"}")));
    }

    @Test
    void floatComparisonsFollowTheOperandTypesAsGroovyLAL() throws Exception {
        final Input nan = log().json("{\"x\":\"NaN\",\"y\":\"1\",\"z\":\"NaN\",\"n\":-0.0}").tag("x", "NaN")
            .tag("y", "1");
        // Two values of the same floating type ordered as primitives: a NaN compares false.
        assertFalse(aborts("parsed.y as Double < parsed.x as Double", nan));
        assertFalse(aborts("tag(\"y\") as Double < tag(\"x\") as Double", nan));
        assertFalse(aborts("parsed.x as Double > parsed.y as Double", nan));
        // Any other comparison too, as Java's primitives: a NaN equals nothing and has no order, -0.0 equals 0.
        assertFalse(aborts("parsed.x as Double > 1", nan));
        assertFalse(aborts("parsed.y as Integer < parsed.x as Double", nan));
        assertFalse(aborts("parsed.x as Double == parsed.z as Double", nan));
        assertTrue(aborts("parsed.n as Double == 0", nan));
        assertFalse(aborts("parsed.n as Double < 0", nan));
        assertFalse(aborts("parsed.v as Double == 0.1f", log().json("{\"v\":0.1}")));
    }

    @Test
    void oneNaNEqualsNothingItselfIncluded() throws Exception {
        final Input in = log().attribute("n", "NaN").json("{}");
        final String rule = "filter { json {} extractor { def q = sourceAttribute(\"n\") as Double\n"
            + " if (%s) { abort {} } } sink {} }";
        assertFalse(run(String.format(rule, "q == q"), in).ctx.shouldAbort());
        assertFalse(run(String.format(rule, "q <= q"), in).ctx.shouldAbort());
        assertTrue(run(String.format(rule, "q != q"), in).ctx.shouldAbort());
    }

    @Test
    void parenthesizedDecimalLiteralComparesAsTheBareOne() throws Exception {
        // a NaN compares false, grouped or not, held in a def or inline, as Java compares it
        final Input nan = log().json("{\"x\":\"NaN\"}");
        assertFalse(aborts("parsed.x as Double > 0.0", nan));
        assertFalse(aborts("parsed.x as Double > (0.0)", nan));
        assertFalse(run("filter { json {} extractor { def limit = 1.0\n if (parsed.x as Double > limit) { abort {} } }"
            + " sink {} }", nan).ctx.shouldAbort());
    }

    // ==================== Arithmetic ====================

    @Test
    void numericChainResultTakesPartInArithmetic() throws Exception {
        final Run run = run("filter { extractor { tag n: tag(\"k\").length() + 1 } sink {} }", log().tag("k", "abc"));

        assertEquals(List.of("n=4"), run.output.tags);
    }

    @Test
    void arithmeticTakesEveryNumberOperand() throws Exception {
        final Input in = log().tag("a", "ab").attribute("a", "abcd").json("{}");
        assertEquals("3", serviceOf(run("filter { json {} extractor { service \"\" + "
            + "((sourceAttribute(\"a\")).length() - 1) } sink {} }", in).output), "a grouped value's method result");
        assertEquals("4", serviceOf(run("filter { json {} extractor { service \"\" + "
            + "((tag(\"a\") as String).length() * 2) } sink {} }", in).output));
        assertEquals("0", serviceOf(run("filter { json {} extractor { service \"\" + (log.timestamp - 1) } sink {} }",
            in).output), "a log field");
        // + joins characters as text; - * / and numeric casts are refused
        assertEquals("ab", serviceOf(run("filter { json {} extractor { service \"\" + "
            + "(tag(\"a\").charAt(0) + tag(\"a\").charAt(1)) } sink {} }", in).output));
        assertEquals("a1", serviceOf(run("filter { json {} extractor { service \"\" + (tag(\"a\").charAt(0) + 1) } "
            + "sink {} }", in).output));
        assertRejected("filter { json {} extractor { service \"\" + (tag(\"a\").charAt(0) - 48) } sink {} }",
            "requires numeric operands");
    }

    @Test
    void plusFollowsTheRuntimeTypesAsGroovy() throws Exception {
        final String dsl = "filter { json {} extractor { tag t: 1 + parsed.n, "
            + "c: toJson(parsed.m).get(\"n\").getAsInt() + 1 } sink {} }";

        assertEquals(List.of("t=6", "c=3"), run(dsl, log().json("{\"n\":5,\"m\":{\"n\":2}}")).output.tags);
        assertEquals(List.of("t=15", "c=3"), run(dsl, log().json("{\"n\":\"5\",\"m\":{\"n\":2}}")).output.tags);
    }

    @Test
    void plusOnValuesOfUnknownTypeAddsNumbersAndJoinsOntoATextANumberOrNull() throws Exception {
        // Groovy LAL did not compile `+` on two values of unknown type: two numbers add, a null joins as "null"
        assertEquals(List.of("n=3", "z=1null"), run("filter { json {} extractor { tag n: parsed.p + parsed.q, "
            + "z: parsed.p + parsed.r } sink {} }", log().json("{\"p\":1,\"q\":2}")).output.tags);
        // a map or a list on the left joins nothing: the log fails, as Groovy has no such text +
        final String json = "{\"base\":{\"a\":1},\"labels\":{\"b\":2},\"xs\":[1,2]}";
        for (final String plus : new String[] {"parsed.base + parsed.labels", "parsed.xs + \"x\""}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run(
                "filter { json {} extractor { tag a: " + plus + " } sink {} }", log().json(json)));
            assertTrue(e.getMessage().contains("takes no part in a text +"), e.getMessage());
        }
    }

    @Test
    void aBooleanJoinsOnlyAStringOnItsLeft() throws Exception {
        assertRejected("filter { json {} extractor { service true + \"-suffix\" } sink {} }",
            "A boolean takes no part in '+'");
        assertRejected("filter { json {} extractor { service \"\" + (1 + (parsed.f as Boolean)) } sink {} }",
            "A boolean takes no part in '+'");
        assertRejected("filter { json {} extractor { service (true) + \"-x\" } sink {} }",
            "A boolean takes no part in '+'");
        // a quoted "true" is a string
        assertEquals("true-suffix", serviceOf(run("filter { json {} extractor { service \"true\" + \"-suffix\" } "
            + "sink {} }", log().json("{}")).output));
        assertRejected("filter { json {} extractor { service (tag(\"a\")).isEmpty() + \"-x\" } sink {} }",
            "A boolean takes no part in '+'");
        // known only when the rule runs: refused then
        assertThrows(Exception.class, () -> run("filter { json {} extractor { service parsed.f + \"-suffix\" } "
            + "sink {} }", log().json("{\"f\":true}")));
        assertEquals("x-true", serviceOf(run("filter { json {} extractor { service \"x-\" + parsed.f } sink {} }",
            log().json("{\"f\":true}")).output));
    }

    @Test
    void stringIsNeverMultiplied() {
        assertRejected("filter { extractor { tag a: tag(\"s\") * 2 } sink {} }", "A string cannot be multiplied");
        assertRejected("filter { extractor { tag a: 2 * \"b\" } sink {} }", "A string cannot be multiplied");
    }

    @Test
    void decimalLiteralsAreDoubles() throws Exception {
        assertEquals(List.of("a=2.5", "b=0.30000000000000004", "p=1.5"), run("filter { json {} extractor { "
            + "tag a: (parsed.n as Integer) + 1.5, b: 0.1 + 0.2, p: 1.50 } sink {} }",
            log().json("{\"n\":1}")).output.tags);
        assertTrue(aborts("parsed.n as Integer < 1.5", log().json("{\"n\":1}")));
    }

    @Test
    void floatPlusAWholeNumberComputesInDouble() throws Exception {
        assertEquals(List.of("f=1.6777217E7"), run("filter { json {} extractor { tag f: (parsed.n as Float) + 1 } "
            + "sink {} }", log().json("{\"n\":16777216}")).output.tags);
    }

    @Test
    void floatsAddInDoubleAsInGroovy() throws Exception {
        assertEquals(List.of("f=1.6777217E7"), run("filter { json {} extractor { "
            + "tag f: (parsed.a as Float) + (parsed.b as Float) } sink {} }", log().json("{\"a\":16777216,\"b\":1}"))
            .output.tags);
    }

    @Test
    void integerLiteralWithALeadingZeroIsOctal() throws Exception {
        // As in Groovy LAL (and Java): 010 is 8, wherever a whole number is written; 08 and 09 are refused.
        assertEquals(List.of("a=8", "s=bc", "e=8"), run("filter { json {} extractor { "
            + "def xs = parsed.xs as java.util.List tag a: 010, s: tag(\"s\").substring(01), e: xs[010] } sink {} }",
            log().tag("s", "abc").json("{\"xs\":[0,1,2,3,4,5,6,7,8,9,10]}")).output.tags);
        assertTrue(aborts("parsed.n as Integer == 010", log().json("{\"n\":8}")));
        assertTrue(aborts("parsed.n as Double == 010", log().json("{\"n\":8}")));
        assertRejected("filter { extractor { tag a: 08 } sink {} }", "octal");
        assertRejected("filter { json {} extractor { def xs = parsed.xs as java.util.List tag a: xs[09] } sink {} }",
            "octal");
        // A zero, and a decimal written with leading zeros, are not octal.
        assertEquals(List.of("z=0", "d=0.5", "e=10.5"), run("filter { extractor { tag z: 0, d: 00.5, e: 010.5 } sink {} }",
            log()).output.tags);
    }

    @Test
    void wholeNumberBeyondALongIsRejected() {
        assertRejected("filter { extractor { tag max: 18446744073709551615 } sink {} }",
            "not a whole number a long holds");
        assertRejected("filter { extractor { metrics { name \"m\" value 18446744073709551615 } } }",
            "not a whole number a long holds");
    }

    @Test
    void wholeNumberDivisionIsADecimalAsInGroovy() throws Exception {
        // As Groovy LAL: 7 / 2 is 3.5, a whole-number cast or setter truncates toward zero, a zero divisor fails.
        assertEquals(List.of("q=3.5", "i=3"), run("filter { json {} extractor { tag q: (parsed.n as Integer) / 2, "
            + "i: ((parsed.n as Integer) / 2) as Integer } sink {} }", log().json("{\"n\":7}")).output.tags);
        assertEquals(List.of("q=-3.5", "i=-3"), run("filter { json {} extractor { tag q: (parsed.n as Long) / 2, "
            + "i: ((parsed.n as Long) / 2) as Long } sink {} }", log().json("{\"n\":-7}")).output.tags);
        assertEquals(3L, run("filter { json {} extractor { count (parsed.n as Integer) / 2 } sink {} }",
            log().json("{\"n\":7}")).output.count);
        assertTrue(aborts("(parsed.n as Integer) / 2 > 3", log().json("{\"n\":7}")));
        final ArithmeticException byZero = assertThrows(ArithmeticException.class, () -> run(
            "filter { json {} extractor { tag q: (parsed.n as Integer) / (parsed.z as Integer) } sink {} }",
            log().json("{\"n\":7,\"z\":0}")));
        assertEquals("Division by zero", byZero.getMessage());
        // A decimal divisor divides in double, as Groovy's: zero makes an infinity.
        assertEquals(List.of("q=1.5", "z=Infinity"), run("filter { json {} extractor { tag q: (parsed.n as Double) / 2, "
            + "z: (parsed.n as Integer) / (parsed.z as Double) } sink {} }", log().json("{\"n\":3,\"z\":0}"))
            .output.tags);
    }

    @Test
    void quotientIsGroovysBigDecimalAndNarrowsAsItDoes() throws Exception {
        final String defs = "filter { json {} extractor { def b = parsed.bytes as Long def s = parsed.streams as Long "
            + "def q = b / s ";
        final Input big = log().json("{\"bytes\":8589934596,\"streams\":2,\"n\":1,\"d\":3}");
        // 4294967298 wraps into an int as BigDecimal.intValue() does: 2, as Groovy gives it.
        assertEquals(List.of("a=2", "b=2", "l=4294967298"), run(defs + "tag a: (b / s) as Integer, b: q as Integer, "
            + "l: q as Long } sink {} }", big).output.tags);
        assertTrue(run(defs + "if (q as Integer == 2) { tag t: \"y\" } } sink {} }", big).output.tags.contains("t=y"));
        // Exact where it terminates, else ten decimals rounded half up; with a double, a double.
        assertEquals(List.of("t=0.3333333333", "h=0.5", "w=7", "d=1.5"), run("filter { json {} extractor { "
            + "def n = parsed.n as Long def d = parsed.d as Long tag t: n / d, h: n / (d - 1), w: (n + 6) / n, "
            + "d: (n / (d - 1)) * 3.0d } sink {} }", big).output.tags);
    }

    @Test
    void quotientKeepsTheOperandsScaleAsGroovyDoes() throws Exception {
        // A terminating quotient with a scale beyond ten keeps it when divided again, so the threshold holds as in Groovy LAL.
        final Run run = run("filter { json {} extractor { "
            + "def rate = (parsed.bytes as Long) / 1048576 / (parsed.seconds as Long) "
            + "if (rate * 1048576 >= 1) { tag hit: \"y\" } tag rate: rate, whole: (rate * 1048576) as Long } sink {} }",
            log().json("{\"bytes\":86399,\"seconds\":86400}"));
        assertEquals(List.of("rate=9.5366327850907E-7", "whole=0"), run.output.tags);
        assertEquals(List.of("r=1052.65590482977805314241", "t=0.3333333333"), run("filter { json {} extractor { "
            + "tag r: (parsed.bytes as Long) / 1048576 / 86400, t: (parsed.a as Long) / (parsed.b as Long) } sink {} }",
            log().json("{\"bytes\":95367431640625,\"a\":1,\"b\":3}")).output.tags);
    }

    @Test
    void quotientKeepsItsZeroDivisorFailureThroughDefsAndParentheses() throws Exception {
        final String defs = "filter { json {} extractor { def n = parsed.n as Long def c = parsed.c as Long "
            + "def t = parsed.t as Long def avg = n / c ";
        final Input zero = log().json("{\"n\":7,\"c\":2,\"t\":0,\"x\":7.0}");
        for (final String value : List.of("avg / t", "(n / c) / t", "n / c * 100 / t", "n / (t / c)")) {
            assertThrows(ArithmeticException.class, () -> run(defs + "tag r: " + value + " } sink {} }", zero),
                value);
        }
        assertEquals(List.of("r=Infinity"), run(defs + "tag r: (parsed.x as Double) / t } sink {} }", zero)
            .output.tags);
        assertEquals(List.of("r=1.75", "p=175.0", "q=7"), run(defs + "tag r: avg / t, p: (n / c) * 100 / t, "
            + "q: n / (c / t) } sink {} }", log().json("{\"n\":7,\"c\":2,\"t\":2}")).output.tags);
    }

    @Test
    void chainedWholeNumberDivisionStillFailsOnAZeroDivisor() throws Exception {
        final String dsl = "filter { json {} extractor { tag r: (parsed.n as Long) / (parsed.c as Long) / (parsed.t as Long) } "
            + "sink {} }";
        assertEquals(List.of("r=1.75"), run(dsl, log().json("{\"n\":7,\"c\":2,\"t\":2}")).output.tags);
        assertThrows(ArithmeticException.class, () -> run(dsl, log().json("{\"n\":7,\"c\":2,\"t\":0}")));
    }

    // ==================== Strings and toString ====================

    @Test
    void aQuotedStringIsTheTextBetweenItsQuotes() throws Exception {
        // its escapes stay as written
        final Run tag = run("filter { extractor { tag t: 'a\\tb', q: \"it's \\\"q\\\"\", "
            + "l: \"${log.service}\" } sink {} }", log());
        // Outside a rateLimit id, ${...} is literal text.
        assertEquals(List.of("t=a\\tb", "q=it's \\\"q\\\"", "l=${log.service}"), tag.output.tags);

        final Run regexp = run("filter { text { regexp \"(?<n>\\d+)\" } extractor { tag n: parsed.n } sink {} }",
            log().text("abc 42"));
        assertEquals(List.of("n=42"), regexp.output.tags);
    }

    @Test
    void concatenationKeepsANullCast() throws Exception {
        final Run run = run("filter { json {} extractor { tag t: \"code=\" + (parsed.code as Integer) } sink {} }",
            log().json("{}"));

        assertEquals(List.of("t=code=null"), run.output.tags);
    }

    @Test
    void nullableNumberConcatenatesAsNull() throws Exception {
        assertEquals(List.of("a=code=null", "b=nullms", "c=3"), run("filter { json {} extractor { "
            + "def n = parsed.code as Integer def c = parsed.c as Integer "
            + "tag a: \"code=\" + n, b: n + \"ms\", c: (c) + 1 } sink {} }", log().json("{\"c\":2}")).output.tags);
        assertEquals(List.of("n=n=null"), run("filter { json {} extractor { def v = toJson(parsed.m) "
            + "tag n: \"n=\" + v?.get(\"n\")?.getAsInt() } sink {} }", log().json("{\"m\":{}}")).output.tags);
    }

    @Test
    void nestedConcatenationKeepsItsGroup() throws Exception {
        assertEquals(List.of("a=51ms"), run("filter { extractor { tag a: 5 + (1 + \"ms\") } sink {} }", log()).output.tags);
    }

    @Test
    void stringConversionIsJavas() throws Exception {
        // A documented difference: Groovy LAL wrote a map in a string as [a:1]. A map is no text in a + though.
        assertEquals(List.of("s={a=1}"), run("filter { json {} extractor { tag s: parsed.m as String } sink {} }",
            log().json("{\"m\":{\"a\":1}}")).output.tags);
        assertThrows(IllegalArgumentException.class, () -> run("filter { json {} extractor { "
            + "tag c: \"meta=\" + parsed.m } sink {} }", log().json("{\"m\":{\"a\":1}}")));
    }

    @Test
    void characterValueIsWrittenAsAString() throws Exception {
        final Run run = run("filter { def ch = tag(\"s\").charAt(0) extractor { tag c: ch, j: \"x\" + ch, "
            + "k: tag(\"s\").charAt(0) + \"y\" } sink { sampler { rateLimit(\"${ch}:1\") { rpm 100 } } } }",
            log().tag("s", "ab"));
        assertEquals(List.of("c=a", "j=xa", "k=ay"), run.output.tags);
        assertEquals(List.of("a:1"), samplerIds());
    }

    @Test
    void arrayToStringWritesItsElements() throws Exception {
        assertEquals(List.of("a=[low, high]", "b=[low, high]"), run("filter { extractor { def parts = tag(\"s\").split(\",\") "
            + "tag a: parts.toString(), b: parts?.toString() } sink {} }", log().tag("s", "low,high")).output.tags);
        // the property spelling is the same call
        assertEquals(List.of("a=[low, high]", "b=[low, high]"), run("filter { extractor { "
            + "def parts = tag(\"s\").split(\",\") tag a: parts.toString, b: parts?.toString } sink {} }",
            log().tag("s", "low,high")).output.tags);
        // a String cast writes them too
        assertEquals(List.of("a=[low, high]"), run("filter { extractor { tag a: tag(\"s\").split(\",\") as String } "
            + "sink {} }", log().tag("s", "low,high")).output.tags);
    }

    @Test
    void toStringAsAPropertyIsTheCallOnAnyValueButAMap() throws Exception {
        // a value of unknown type, as a def and read through parsed: the call, a list by its elements
        assertEquals(List.of("a=7", "b=1", "c=[1, 2]", "d=[]", "e=x"), run("filter { json {} extractor { def v = parsed.v "
            + "tag a: v.toString, b: v?.toString.length(), c: parsed.l.toString, d: parsed.e?.toString, "
            + "e: parsed.s.toString } sink {} }", log().json("{\"v\":7,\"l\":[1,2],\"e\":[],\"s\":\"x\"}")).output.tags);
        // a map's property is its key
        assertEquals(List.of("k=y"), run("filter { json {} extractor { tag k: parsed.m.toString } sink {} }",
            log().json("{\"m\":{\"toString\":\"y\"}}")).output.tags);
    }

    // ==================== Chains, properties and subscripts ====================

    @Test
    void chainAfterTagAndSourceAttributeRuns() throws Exception {
        final Run run = run("filter { extractor { tag t: tag(\"k\").trim(), s: sourceAttribute(\"os\").toUpperCase() } "
            + "sink {} }", log().tag("k", "  v  ").attribute("os", "ios"));

        assertEquals(List.of("t=v", "s=IOS"), run.output.tags);
    }

    @Test
    void negatedChainAfterTagIsNotReadAsTheBareTag() throws Exception {
        // The shape of lal/ios-metrickit.yaml's guard.
        final String dsl = "filter { if (!tag(\"name\").startsWith(\"metrickit.diagnostic.\")) { abort {} } sink {} }";

        assertTrue(run(dsl, log().tag("name", "app.log")).ctx.shouldAbort());
        assertFalse(run(dsl, log().tag("name", "metrickit.diagnostic.crash")).ctx.shouldAbort());
    }

    @Test
    void chainOnALiteralAndAfterProcessRegistryRuns() throws Exception {
        final Run run = run("filter { json {} extractor { tag t: \"a-b\".replace(\"-\", \"_\"), "
            + "p: ProcessRegistry.generateVirtualLocalProcess(parsed.s as String, parsed.i as String).isEmpty() } "
            + "sink {} }", log().json("{\"s\":\"svc\",\"i\":\"inst\"}"));

        assertEquals(List.of("t=a_b", "p=false"), run.output.tags);
        assertRejected("filter { extractor { tag t: ProcessRegistry.LOCAL_VIRTUAL_PROCESS } sink {} }",
            "call one of its methods");
    }

    @Test
    void builtInDefInitializerAndMethodArgumentKeepTheirChains() throws Exception {
        final Run run = run("filter { json {} extractor { "
            + "def b = toJson(parsed.a).getAsJsonObject(\"b\") "
            + "def key = parsed.key as String "
            + "tag c: b?.get(\"c\")?.getAsString(), d: b?.get(key.trim())?.getAsString() } sink {} }",
            log().json("{\"a\":{\"b\":{\"c\":\"v\",\"d\":\"w\"}},\"key\":\" d \"}"));

        assertEquals(List.of("c=v", "d=w"), run.output.tags);
    }

    @Test
    void chainOnAParenthesizedConcatenationAppliesToTheWhole() throws Exception {
        assertEquals(List.of("u=API-CART", "s=true"), run("filter { extractor { "
            + "tag u: (\"api-\" + tag(\"path\")).toUpperCase(), s: (\"api-\" + tag(\"path\")).startsWith(\"api-\") } "
            + "sink {} }", log().tag("path", "cart")).output.tags.subList(0, 2));
    }

    @Test
    void methodArgumentKeepsItsTrailingCall() throws Exception {
        // parsed.* without a parser reads the metadata: an int span id, then toString().
        final Run run = run("filter { extractor { tag i: \"0,5\".indexOf(parsed.traceContext.spanId.toString()) } "
            + "sink {} }", log().spanId(5));

        assertEquals(List.of("i=2"), run.output.tags);
    }

    @Test
    void primitiveArgumentForAnObjectParameterIsBoxedAndKeepsANull() throws Exception {
        final String contains = "filter { json {} extractor { def codes = parsed.codes as java.util.List "
            + "if (codes.contains(parsed.code as Integer)) { tag hit: \"y\" } } sink {} }";
        assertEquals(List.of("hit=y"), run(contains, log().json("{\"codes\":[200,404],\"code\":404}")).output.tags);
        assertTrue(run(contains, log().json("{\"codes\":[200,404]}")).output.tags.isEmpty());

    }

    @Test
    void boxedArgumentForAPrimitiveParameterIsUnboxed() throws Exception {
        assertEquals(List.of("r=ab", "i=0"), run("filter { extractor { def c = tag(\"s\").charAt(0) "
            + "tag r: tag(\"s\").replace(c, c), i: tag(\"s\").indexOf(c) } sink {} }", log().tag("s", "ab"))
            .output.tags);
    }

    @Test
    void safeNavigationOnAMethodResult() throws Exception {
        final Run run = run("filter { json {} extractor { def m = parsed.m as java.util.Map "
            + "tag t: m.get(\"token\")?.toString() } sink {} }", log().json("{\"m\":{\"token\":\"abc\"}}"));

        assertEquals(List.of("t=abc"), run.output.tags);
    }

    @Test
    void safeMethodCallKeepsItsArguments() throws Exception {
        assertEquals(List.of("hex=ffffffffffffffff"), run("filter { json {} extractor { "
            + "def id = parsed.id as java.math.BigInteger tag hex: id?.toString(16) } sink {} }",
            log().json("{\"id\":18446744073709551615}")).output.tags);
        assertRejected("filter { json {} extractor { tag hex: parsed.id?.toString(16) } sink {} }",
            "Cannot resolve method Object.toString(int)");
        assertEquals(List.of("t=ABC"), run("filter { json {} extractor { def m = parsed.m as java.util.Map "
            + "tag t: (m.get(\"token\") as String)?.toUpperCase() } sink {} }",
            log().json("{\"m\":{\"token\":\"abc\"}}")).output.tags);
    }

    @Test
    void trimOnAnObjectAMethodReturnedNeedsAString() throws Exception {
        assertRejected("filter { json {} extractor { def m = parsed.payload as java.util.Map "
            + "tag s: m.get(\"status\")?.trim() } sink {} }", "Cannot resolve method Object.trim()");
        assertEquals(List.of("s=503"), run("filter { json {} extractor { def m = parsed.payload as java.util.Map "
            + "tag s: (m.get(\"status\") as String)?.trim() } sink {} }", log().json("{\"payload\":{\"status\":\" 503 \"}}"))
            .output.tags);
    }

    @Test
    void defOfATypedObjectResultTakesTrimOnlyAsAString() {
        assertRejected("filter { json {} extractor { def m = parsed.payload as java.util.Map def status = m.get(\"status\") "
            + "tag s: status?.trim() } sink {} }", "Cannot resolve method Object.trim()");
    }

    @Test
    void typedObjectThroughParenthesesTakesTrimOnlyAsAString() {
        assertRejected("filter { json {} extractor { def m = parsed.payload as java.util.Map def status = m.get(\"status\") "
            + "tag s: (status)?.trim() } sink {} }", "Cannot resolve method Object.trim()");
    }

    @Test
    void anInterfaceValueAnswersObjectsMethods() throws Exception {
        assertEquals("{a=b}", serviceOf(run("filter { json {} extractor { def m = parsed.m as java.util.Map\n"
            + " service m.toString() } sink {} }", log().json("{\"m\":{\"a\":\"b\"}}")).output));
    }

    @Test
    void propertyOfAMissingValueFailsAsInGroovyAndSpreadsOverAList() throws Exception {
        assertThrows(NullPointerException.class,
            () -> run("filter { json {} extractor { tag b: parsed.a.b } sink {} }", log().json("{}")));
        assertTrue(run("filter { json {} extractor { tag b: parsed?.a?.b } sink {} }", log().json("{}"))
            .output.tags.isEmpty());
        assertEquals(List.of("names=x,y"), run("filter { json {} extractor { tag names: parsed.backends.name } "
            + "sink {} }", log().json("{\"backends\":[{\"name\":\"x\"},null,{\"name\":\"y\"}]}")).output.tags);

        // As Groovy LAL, the shipped sampled-trace rule stops at a missing client_process, not writing a trace
        // without its process.
        final String rule = shippedRule("k8s-service.yaml", "network-profiling-slow-trace");
        assertThrows(NullPointerException.class, () -> run(rule, SampledTraceBuilder.class, log()
            .tag("LOG_KIND", "NET_PROFILING_SAMPLED_TRACE")
            .json("{\"latency\":1,\"reason\":\"SLOW\",\"server_process\":{\"process_id\":\"server-1\"}}")));
    }

    @Test
    void mapPropertyReadsItsKey() throws Exception {
        assertEquals(List.of("size=2048", "entries=2"), run("filter { json {} extractor { "
            + "def m = parsed.file as java.util.Map tag size: m.size, entries: m.size() } sink {} }",
            log().json("{\"file\":{\"size\":2048,\"name\":\"a\"}}")).output.tags);
    }

    @Test
    void propertyOfAListIsItsOwnBeforeItsElements() throws Exception {
        assertEquals(List.of("e=y"), run("filter { json {} extractor { if (parsed.items.empty) { tag e: \"y\" } } "
            + "sink {} }", log().json("{\"items\":[]}")).output.tags);
    }

    @Test
    void knownCollectionPropertyNamingItsOwnMethodCallsIt() throws Exception {
        final String dsl = "filter { json {} extractor { def rows = parsed.rows as java.util.List "
            + "if (rows.isEmpty) { tag empty: \"y\" } } sink {} }";
        assertEquals(List.of("empty=y"), run(dsl, log().json("{\"rows\":[]}")).output.tags);
        assertTrue(run(dsl, log().json("{\"rows\":[{\"id\":42}]}")).output.tags.isEmpty());
    }

    @Test
    void knownCollectionSizeAndTrimOnAKnownType() throws Exception {
        assertEquals(List.of("c=count=2"), run("filter { json {} extractor { def samples = parsed.samples as java.util.List "
            + "tag c: \"count=\" + samples.size } sink {} }", log().json("{\"samples\":[{\"size\":2},{\"size\":3}]}"))
            .output.tags);
        assertRejected("filter { json {} extractor { def payload = toJson(parsed.payload) "
            + "tag s: payload.get(\"status\")?.trim() } sink {} }", "JsonElement.trim()");
    }

    @Test
    void arrayLengthReads() throws Exception {
        assertEquals(List.of("size=n=3", "n=2"), run("filter { json {} extractor { "
            + "def parts = (parsed.path as String).split(\"/\") "
            + "tag size: \"n=\" + (parsed.msg as String).split(\"l\").length, n: parts.length } sink {} }",
            log().json("{\"msg\":\"hello\",\"path\":\"a/b\"}")).output.tags);
    }

    @Test
    void listSubscriptPastTheEndIsNull() throws Exception {
        final String dsl = "filter { json {} extractor { def xs = parsed.items as java.util.List "
            + "tag first: xs[0] } sink {} }";

        assertTrue(run(dsl, log().json("{\"items\":[]}")).output.tags.isEmpty());
        assertEquals(List.of("first=a"), run(dsl, log().json("{\"items\":[\"a\"]}")).output.tags);
    }

    @Test
    void subscriptIndexesAnArrayAndFailsOnANull() throws Exception {
        assertEquals(List.of("second=b"), run("filter { extractor { tag second: tag(\"path\").split(\"/\")[1] } "
            + "sink {} }", log().tag("path", "a/b")).output.tags);
        assertThrows(NullPointerException.class, () -> run("filter { json {} extractor { "
            + "def xs = parsed.items as java.util.List tag f: xs[0] } sink {} }", log().json("{}")));
    }

    @Test
    void jsonArrayIndexPastTheEndFails() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("filter { json {} extractor { "
            + "def errors = toJsonArray(parsed.errors) tag first: errors[0] } sink {} }", log().json("{\"errors\":[]}")));
    }

    // ==================== The callable list ====================

    @Test
    void writesToTheParsedMapAreRefused() {
        final String json = "filter { json {} extractor { ";
        assertRejected(json + "def old = parsed.getMap().put(\"service\", \"tmp\") } sink {} }",
            "Map.put(...) is not supported");
        // Removing a body key, or a copied one, would change what parsed.<key> reads.
        assertRejected(json + "def gone = parsed.getMap().remove(\"service\") } sink {} }", "Map.remove(...) is not supported");
        assertRejected(json + "def m = parsed.getMap() def gone = m.clear() } sink {} }", "Map.clear(...) is not supported");
        assertRejected(json + "def m = parsed.m as java.util.Map def old = m.put(\"code\", 5) } sink {} }",
            "Map.put(...) is not supported");
        assertRejected(json + "def vs = parsed.vs as java.util.List def v = vs.remove(0) } sink {} }",
            "List.remove(...) is not supported");
    }

    @Test
    void logBuilderMutationsAreRefused() {
        for (final String mutation : List.of("log.setService(\"api\")", "log.setService(\"api\").setEndpoint(\"/home\")",
                "log.setTimestamp(1000L)", "log.clearService()", "log.setLayer(\"MESH\")", "log.clearLayer()")) {
            assertRejected("filter { json {} extractor { def old = " + mutation + " } sink {} }", "is not supported");
        }
    }

    @Test
    void reflectionAndUnlistedMethodsAreRefused() {
        assertRejected("filter { json {} extractor { tag c: parsed.x.getClass() } sink {} }",
            "Object.getClass(...) is not supported");
        assertRejected("filter { extractor { tag c: tag(\"s\").getBytes() } sink {} }",
            "String.getBytes(...) is not supported");
        assertRejected("filter { extractor { tag c: log.getClass() } sink {} }", "getClass(...) is not supported");
        assertRejected("filter { json {} extractor { def r = parsed.r as java.lang.Runtime } sink {} }",
            "A def cannot be cast as java.lang.Runtime");
        assertRejected("filter { extractor { tag p: ProcessRegistry.toString() } sink {} }",
            "ProcessRegistry has no method toString()");
    }

    @Test
    void readingAPropertyAtRunTimeIsCheckedToo() {
        assertThrows(IllegalArgumentException.class, () -> run("filter { json {} extractor { tag c: parsed.s.bytes } "
            + "sink {} }", log().json("{\"s\":\"abc\"}")));
        assertThrows(IllegalArgumentException.class, () -> run("filter { json {} extractor { tag c: parsed.n.class } "
            + "sink {} }", log().json("{\"n\":1}")));
    }

    @Test
    void onlyAnInstanceToStringOrABigIntegerRadixResolves() throws Exception {
        assertRejected("filter { json {} extractor { def n = parsed.n as Long tag hex: n.toString(16) } sink {} }",
            "Long.toString(...) is a static method");
        assertEquals(List.of("hex=ff", "n=255"), run("filter { json {} extractor { def b = parsed.n as java.math.BigInteger "
            + "def n = parsed.n as Long tag hex: b.toString(16), n: n.toString() } sink {} }", log().json("{\"n\":255}"))
            .output.tags);
    }

    @Test
    void unresolvedCallsNameTheList() {
        assertRejected("filter { extractor { tag p: ProcessRegistry.unknown(\"svc\", \"i\") } sink {} }",
            "the static calls are");
        assertRejected("filter { extractor { tag a: tag(\"s\").trmi() } sink {} }", "(see the LAL documentation)");
    }

    @Test
    void sharedStringMethodsCompile() throws Exception {
        assertEquals(List.of("a=X-B", "b=a-c", "c=ab"), run("filter { extractor { "
            + "tag a: tag(\"s\").replaceAll(\"[a]\", \"x\").replaceFirst(\"-b\", \"-B\").toUpperCase(), "
            + "b: tag(\"s\").replace(\"b\", \"c\"), c: tag(\"s\").substring(0, 1).concat(\"b\") } sink {} }",
            log().tag("s", "a-b")).output.tags);
    }

    // ==================== Metrics ====================

    @Test
    void metricNameIsTheValueOfItsExpression() throws Exception {
        run("filter { json {} extractor { metrics { name parsed.metric labels k: \"v\" value 1 } } }",
            log().json("{\"metric\":\"m1\"}"));

        assertEquals("m1", onlySample().getName());
    }

    @Test
    void metricValueAndLabelCastsConvert() throws Exception {
        run("filter { json {} extractor { metrics { name \"m\" labels n: parsed.n as Long "
                + "value parsed.v as Double } } }",
            log().json("{\"n\":\"5\",\"v\":\"1.5\"}"));

        assertEquals(1.5, onlySample().getValue());
        assertEquals("5", onlySample().getLabels().get("n"));
        assertRejected("filter { json {} extractor { metrics { name \"m\" value parsed.v as String } } }",
            "metrics value is a number");
    }

    @Test
    void repeatedMetricsStatementsRunInOrder() throws Exception {
        // Each statement runs, so the last of each sets the sample, as in Groovy LAL; a map literal keeps a
        // repeated key's last value.
        run("filter { extractor { metrics { name \"a\" name \"b\" labels k: \"1\" labels j: \"2\", j: \"3\" "
            + "value 1 value 2 } } }", log());
        final Sample sample = onlySample();
        assertEquals("b", sample.getName());
        assertEquals(Map.of("j", "3"), sample.getLabels());
        assertEquals(2.0, sample.getValue());
    }

    @Test
    void metricsStatementsRunInSourceOrder() throws Exception {
        run("filter { json {} extractor { metrics { name \"m\" value parsed.v as Double timestamp parsed.t as Long } } }",
            log().json("{\"v\":3.5,\"t\":1700000000000}"));
        final Sample sample = onlySample();
        assertEquals(3.5, sample.getValue());
        assertEquals(1700000000000L, sample.getTimestamp());
        // The statement written first fails first.
        assertThrows(ArithmeticException.class, () -> run("filter { json {} extractor { metrics { name \"m\" "
            + "value (2 / 0) as Double timestamp parsed.a.b as Long } } }", log().json("{}")));
        final Exception other = assertThrows(Exception.class, () -> run("filter { json {} extractor { metrics { "
            + "name \"m\" timestamp parsed.a.b as Long value (2 / 0) as Double } } }", log().json("{}")));
        assertFalse(other instanceof ArithmeticException, other.toString());
    }

    @Test
    void nginxErrorMetricTakesTheParsedTimestamp() throws Exception {
        final Run run = run(shippedRule("nginx.yaml", "nginx-error-log"), log()
            .tag("LOG_KIND", "NGINX_ERROR_LOG").text("2021/01/01 00:00:00 [error] 123#123: test error message"));

        final long parsed = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse("2021/01/01 00:00:00").getTime();
        assertEquals(parsed, run.output.getTimestamp());
        assertEquals(parsed, onlySample().getTimestamp());
    }

    // ==================== Sampler and rateLimit ====================

    @Test
    void samplersRunInSourceOrderAndTheLastDecides() throws Exception {
        final Run lastKeeps = run("filter { sink { sampler { if (tag(\"a\") == \"x\") { rateLimit(\"drop\") { rpm 0 } } "
            + "rateLimit(\"keep\") { rpm 100 } } } }", log().tag("a", "x"));
        assertTrue(lastKeeps.ctx.shouldSave());

        final Run lastDrops = run("filter { sink { sampler { rateLimit(\"keep2\") { rpm 100 } "
            + "if (tag(\"a\") == \"x\") { rateLimit(\"drop2\") { rpm 0 } } } } }", log().tag("a", "x"));
        assertFalse(lastDrops.ctx.shouldSave());
    }

    @Test
    void rateLimitOutsideASamplerIsRejected() {
        assertRejected("filter { sink { if (tag(\"a\") == \"x\") { rateLimit(\"k\") { rpm 0 } } } }",
            "rateLimit(...) is not supported in a sink: put it in a sampler {}");
    }

    @Test
    void interpolatedIdConvertsItsCastAndKeepsAnEscapedDollar() throws Exception {
        run("filter { json {} sink { sampler { rateLimit(\"\\${n}:${parsed.n as Integer}\") { rpm 1 } } } }",
            log().json("{\"n\":\"007\"}"));

        assertEquals(List.of("${n}:7"), samplerIds());
    }

    @Test
    void anEscapedDollarThatStartsNoInterpolationStaysAsWritten() throws Exception {
        run("filter { json {} sink { sampler { rateLimit(\"x\\$2-${parsed.n as Integer}\") { rpm 1 } } } }",
            log().json("{\"n\":\"007\"}"));

        assertEquals(List.of("x\\$2-7"), samplerIds());
    }

    @Test
    void unsupportedIdIsRejected() {
        assertRejected("filter { sink { sampler { rateLimit(\"${log.service == 'x'}\") { rpm 1 } } } }",
            "is not a value");
        assertRejected("filter { sink { sampler { rateLimit('${log.service}') { rpm 1 } } } }", "single-quoted");
        assertRejected("filter { sink { sampler { rateLimit(\"$log\") { rpm 1 } } } }", "without braces");
        assertRejected("filter { sink { sampler { rateLimit(\"a\") { rpm 3000000000 } } } }", "Java int");
    }

    // ==================== Harness ====================

    public static final class PojoInput implements ToJson {
        private final String name;

        public PojoInput(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public ByteBuffer getPayload() {
            return ByteBuffer.wrap(new byte[] {65});
        }

        @Override
        public String toJson() {
            return "{\"name\":\"" + name + "\"}";
        }
    }

    private static String serviceOf(final LogBuilder output) throws Exception {
        final Field field = LogBuilder.class.getDeclaredField("service");
        field.setAccessible(true);
        return (String) field.get(output);
    }

    /**
     * Swaps the sink for the traffic listener, recording the service, instance and endpoint traffic it builds.
     */
    private List<ISource> recordTraffic() throws Exception {
        final List<ISource> received = new ArrayList<>();
        final SourceReceiver receiver = mock(SourceReceiver.class);
        doAnswer(i -> received.add(i.getArgument(0))).when(receiver).receive(any());
        final NamingControl naming = mock(NamingControl.class);
        when(naming.formatServiceName(anyString())).thenAnswer(i -> i.getArgument(0));
        when(naming.formatInstanceName(anyString())).thenAnswer(i -> i.getArgument(0));
        when(naming.formatEndpointName(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(SourceReceiver.class)).thenReturn(receiver);
        when(services.getService(NamingControl.class)).thenReturn(naming);
        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        when(core.provider()).thenReturn(services);
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.find(CoreModule.NAME)).thenReturn(core);
        setField(filterSpec, "sinkListenerFactories",
            List.of(new TrafficSinkListener.Factory(manager, new LogAnalyzerModuleConfig())));
        return received;
    }

    private static List<String> describe(final List<ISource> traffic) {
        final List<String> described = new ArrayList<>();
        for (final ISource source : traffic) {
            if (source instanceof ServiceMeta) {
                described.add("service " + ((ServiceMeta) source).getName());
            } else if (source instanceof ServiceInstanceUpdate) {
                described.add("instance " + ((ServiceInstanceUpdate) source).getName());
            } else if (source instanceof EndpointMeta) {
                described.add("endpoint " + ((EndpointMeta) source).getEndpoint());
            }
        }
        return described;
    }

    @SuppressWarnings("unchecked")
    private static String shippedRule(final String file, final String name) throws Exception {
        final Path path = Paths.get("..", "..", "server-starter", "src", "main", "resources", "lal", file);
        final Map<String, Object> config = new Yaml().load(Files.readString(path));
        for (final Map<String, Object> rule : (List<Map<String, Object>>) config.get("rules")) {
            if (name.equals(rule.get("name"))) {
                return (String) rule.get("dsl");
            }
        }
        throw new IllegalArgumentException(name + " not in " + file);
    }

    /**
     * The output type the rules write to, recording what they set.
     */
    public static class RecordingOutput extends LogBuilder {
        private final List<String> tags = new ArrayList<>();
        private double ratio;
        private long count = -1L;
        private Kind kind;
        private DetectPoint detectPoint;

        /**
         * Records the tags LogBuilder keeps: one with an empty or null value is not added.
         */
        @Override
        public void addTag(final String key, final String value) {
            if (value != null && !value.isEmpty()) {
                tags.add(key + "=" + value);
            }
            super.addTag(key, value);
        }

        public void setRatio(final double ratio) {
            this.ratio = ratio;
        }

        public void setCount(final long count) {
            this.count = count;
        }

        public void setKind(final Kind kind) {
            this.kind = kind;
        }

        public void setDetectPoint(final DetectPoint detectPoint) {
            this.detectPoint = detectPoint;
        }
    }

    public enum Kind {
        BIG
    }

    private boolean aborts(final String condition, final Input input) throws Exception {
        return run("filter { json {} if (" + condition + ") { abort {} } sink {} }", input).ctx.shouldAbort();
    }

    private static final class Run {
        private final ExecutionContext ctx;
        private final RecordingOutput output;

        private Run(final ExecutionContext ctx) {
            this.ctx = ctx;
            this.output = ctx.output() instanceof RecordingOutput ? (RecordingOutput) ctx.output() : null;
        }
    }

    private static final class Input {
        private final LogData.Builder log = LogData.newBuilder().setService("svc");
        private final Map<String, String> attributes = new HashMap<>();
        private int spanId;

        Input spanId(final int spanId) {
            this.spanId = spanId;
            return this;
        }

        Input tag(final String key, final String value) {
            log.setTags(LogTags.newBuilder(log.getTags())
                               .addData(KeyStringValuePair.newBuilder().setKey(key).setValue(value)));
            return this;
        }

        Input attribute(final String key, final String value) {
            attributes.put(key, value);
            return this;
        }

        Input json(final String body) {
            log.setBody(LogDataBody.newBuilder().setJson(JSONLog.newBuilder().setJson(body)));
            return this;
        }

        Input text(final String body) {
            log.setBody(LogDataBody.newBuilder().setText(TextLog.newBuilder().setText(body)));
            return this;
        }
    }

    private static Input log() {
        return new Input();
    }

    private Run run(final String dsl, final Input input) throws Exception {
        return run(dsl, RecordingOutput.class, input);
    }

    private Run run(final String dsl, final Class<?> outputType, final Input input) throws Exception {
        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setOutputType(outputType);
        final LalExpression expr = generator.compile(dsl);
        final LogMetadata metadata = LogMetadata.builder()
                                                .service("svc")
                                                .layer("GENERAL")
                                                .timestamp(1L)
                                                .sourceAttributes(input.attributes)
                                                .traceContext(LogMetadata.TraceContext.builder()
                                                                                     .spanId(input.spanId)
                                                                                     .build())
                                                .build();
        final ExecutionContext ctx = new ExecutionContext().init(metadata, input.log);
        ctx.metricsContainer(metrics);
        expr.execute(filterSpec, ctx);
        return new Run(ctx);
    }

    private static void assertRejected(final String dsl, final String messagePart) {
        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setOutputType(RecordingOutput.class);
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> generator.compile(dsl), dsl);
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    private Sample onlySample() {
        assertEquals(1, metrics.size());
        assertEquals(1, metrics.get(0).samples.length);
        return metrics.get(0).samples[0];
    }

    @SuppressWarnings("unchecked")
    private List<String> samplerIds() throws Exception {
        final Field field = filterSpec.sampler().getClass().getDeclaredField("rateLimitSamplersByString");
        field.setAccessible(true);
        return new ArrayList<>(((Map<String, ?>) field.get(filterSpec.sampler())).keySet());
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
            @Override
            public LogSinkListener parse(final LogMetadata metadata, final Object input, final ExecutionContext ctx) {
                return this;
            }

            @Override
            public void build() {
                saved++;
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
