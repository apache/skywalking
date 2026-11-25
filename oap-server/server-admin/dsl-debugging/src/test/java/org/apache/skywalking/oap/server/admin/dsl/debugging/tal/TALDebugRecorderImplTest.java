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

package org.apache.skywalking.oap.server.admin.dsl.debugging.tal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.opentelemetry.proto.trace.v1.Span;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.ExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Granularity;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.function.ApdexArgument;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TalStaticBindingHook;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The records a TAL session publishes: the object the runner passed as the context, then the list of its spans, each
 * with the span as it arrived and the rule's run on it.
 */
class TALDebugRecorderImplTest {
    private static final RuleKey KEY = new RuleKey(Catalog.TAL, "native-segment", "native-segment");

    @BeforeAll
    static void declareScopes() {
        if (DefaultScopeDefine.classOf(ServiceMeta.class.getSimpleName()) == null) {
            new DefaultScopeDefine.Listener().notify(ServiceMeta.class);
        }
    }

    @Test
    void aNativeRecordListsEverySpanAndPutsTheBuildOnTheSpanThatQueuedIt() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final SpanObject entry = span(0, SpanType.Entry);
        final SpanObject exit = span(1, SpanType.Exit);
        final SwNativeSegmentState segment = segment(entry, exit);
        final Object[] defs = new Object[1];
        final ExecutionContext ctx = new ExecutionContext();

        recorder.beginSegment(segment);
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment, defs);
        recorder.beginSpan(ctx, null, null);
        recorder.step(ctx, "first", "block", 2, "first {", null);
        defs[0] = "svc";
        recorder.step(ctx, "first", "segmentDef", 3, "segmentDef owner = segment().service", "svc");
        recorder.beginSpan(ctx, null, null);
        recorder.condition(ctx, "filter", 5, "if (span.spanType == \"Entry\")", true);
        recorder.step(ctx, "extractor", "build", 8, "build {", 0);
        recorder.endSpan(ctx);
        ctx.bindNativeRun(SpanMetadata.builder().build(), exit, segment, defs);
        recorder.beginSpan(ctx, null, null);
        recorder.condition(ctx, "filter", 5, "if (span.spanType == \"Entry\")", false);
        recorder.endSpan(ctx);
        recorder.beginBuild(entry, 0);
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment, defs);
        final ServiceMeta meta = new ServiceMeta();
        meta.setName("svc:op0");
        meta.setLayer(Layer.GENERAL);
        recorder.source(ctx, "build", 8, "source ServiceMeta {", meta);
        recorder.endSegment();

        final JsonObject record = onlyRecord(recorder);
        final JsonObject context = record.getAsJsonObject("context");
        assertEquals("segment", context.get("kind").getAsString());
        assertEquals("seg1", context.getAsJsonObject("segment").get("traceSegmentId").getAsString());
        assertEquals(42, context.getAsJsonObject("derived").get("segmentLatency").getAsInt());
        final JsonArray spans = record.getAsJsonArray("spans");
        assertEquals(2, spans.size(), "every span of the segment is listed");
        assertEquals(1, spans.get(1).getAsJsonObject().getAsJsonObject("span").get("spanId").getAsInt());
        final JsonObject spanZero = spans.get(0).getAsJsonObject().getAsJsonObject("span");
        assertEquals(0, spanZero.get("spanId").getAsInt(), "a field at its default is printed: " + spanZero);
        assertEquals("Entry", spanZero.get("spanType").getAsString());

        final JsonObject first = spans.get(0).getAsJsonObject().getAsJsonObject("run");
        final JsonArray steps = first.getAsJsonArray("steps");
        assertEquals(5, steps.size(), "first {} and the run share span 0, and the build comes last: " + steps);
        assertEquals("first", steps.get(0).getAsJsonObject().get("stage").getAsString());
        assertEquals(3, steps.get(1).getAsJsonObject().get("sourceLine").getAsInt(),
            "a DSL line reads as the line of the file");
        assertEquals("svc", steps.get(1).getAsJsonObject().get("value").getAsString());
        assertTrue(steps.get(2).getAsJsonObject().get("result").getAsBoolean());
        assertEquals("build", steps.get(4).getAsJsonObject().get("stage").getAsString());
        assertEquals(0, steps.get(4).getAsJsonObject().get("output").getAsInt());
        final JsonObject source = first.getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertEquals("ServiceMeta", source.get("scope").getAsString());
        assertEquals("svc", first.getAsJsonObject("state").getAsJsonObject("segmentDefs").get("owner")
                                 .getAsString());
        assertFalse(spans.get(1).getAsJsonObject().getAsJsonObject("run").getAsJsonArray("steps").get(0)
                         .getAsJsonObject().get("result").getAsBoolean());
    }

    @Test
    void aBlockCaptureKeepsTheBlocksAndEveryOutput() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.BLOCK, 1);
        final SpanObject entry = span(0, SpanType.Entry);
        final SwNativeSegmentState segment = segment(entry);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment, new Object[0]);

        recorder.beginSegment(segment);
        recorder.beginSpan(ctx, null, null);
        recorder.condition(ctx, "filter", 5, "if (x)", true);
        recorder.step(ctx, "filter", "def", 6, "def op = span.operationName", "op0");
        recorder.step(ctx, "extractor", "block", 7, "extractor {", null);
        recorder.step(ctx, "extractor", "writeNetworkAlias", 8, "writeNetworkAlias peer", "10.0.0.1:80");
        final ServiceMeta meta = new ServiceMeta();
        meta.setName("svc");
        meta.setLayer(Layer.GENERAL);
        recorder.source(ctx, "build", 9, "source ServiceMeta {", meta);
        recorder.metricSkipped(ctx, "build", 10, "metrics {", "m", "the entity's layer is not registered");
        recorder.endSpan(ctx);
        recorder.endSegment();

        final JsonObject run = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                   .getAsJsonObject("run");
        final JsonArray steps = run.getAsJsonArray("steps");
        assertEquals(4, steps.size(), "the block and the three outputs, without the if and the def: " + steps);
        assertEquals("block", steps.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("source", steps.get(2).getAsJsonObject().get("kind").getAsString());
        assertEquals(2, steps.get(3).getAsJsonObject().get("output").getAsInt(), "each output step names its output");
        assertEquals("10.0.0.1:80", run.getAsJsonArray("outputs").get(0).getAsJsonObject().get("address")
                                       .getAsString());
    }

    @Test
    void anOtlpRecordIsOneSpanWithItsAbortAndThePairingHalf() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("web").kind(SpanMetadata.KIND_CLIENT).spanId("s1").build(),
            Span.newBuilder().setName("GET /cart").build(), Map.of("service.name", "web"), Map.of());

        recorder.beginSpan(ctx, Map.of("service.name", "web"), Map.of());
        recorder.step(ctx, "extractor", "prepareNetworkAlias", 4, "prepareNetworkAlias \"cart:80\"", "cart:80");
        recorder.pairing("caller", "notSampled", "cart:80", null);
        recorder.endSpan(ctx);

        recorder.beginSpan(ctx, Map.of(), Map.of());
        recorder.step(ctx, "filter", "abort", 2, "abort {}", null);
        recorder.endSpan(ctx);

        final List<ExecutionRecord> records = recorder.snapshotRecords();
        assertEquals(2, records.size(), "one record per span the runner passed");
        final JsonObject first = body(records.get(0));
        assertEquals("span", first.getAsJsonObject("context").get("kind").getAsString());
        assertEquals("web", first.getAsJsonObject("context").getAsJsonObject("resource").get("service.name")
                                 .getAsString());
        final JsonObject entry = first.getAsJsonArray("spans").get(0).getAsJsonObject();
        assertEquals("GET /cart", entry.getAsJsonObject("span").get("name").getAsString());
        assertEquals("CLIENT", entry.getAsJsonObject("meta").get("kind").getAsString());
        final JsonObject pairing = entry.getAsJsonObject("run").getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertEquals("pairing", pairing.get("kind").getAsString());
        assertEquals("notSampled", pairing.get("outcome").getAsString());
        assertEquals(4, pairing.get("sourceLine").getAsInt(), "the half names the statement that asked for it");
        final JsonArray steps = entry.getAsJsonObject("run").getAsJsonArray("steps");
        final JsonObject last = steps.get(steps.size() - 1).getAsJsonObject();
        assertEquals("pairing", last.get("kind").getAsString(), "decided after the run, so its step comes last");
        assertEquals(0, last.get("output").getAsInt());

        final JsonObject aborted = body(records.get(1)).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                       .getAsJsonObject("run");
        assertFalse(aborted.get("continueOn").getAsBoolean(), "an aborted run is published, as LAL's are");
        assertEquals("abort {} at line 2", aborted.get("reason").getAsString());
    }

    @Test
    void aPairingHalfCarriesTheServiceTheRunSetAndItsOwnPeer() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("web").kind(SpanMetadata.KIND_CLIENT).traceId("t1").spanId("s1")
                             .build(), Span.getDefaultInstance(), Map.of(), Map.of());

        recorder.beginSpan(ctx, Map.of(), Map.of());
        // The rule renamed the service before it asked for its half: the half is built from the run's metadata.
        ctx.getMeta().setService("web-v2");
        recorder.step(ctx, "extractor", "prepareNetworkAlias", 4, "prepareNetworkAlias \"cart:80\"", "cart:80");
        recorder.pairing("caller", "sent", "cart:80", "an-earlier-rule");
        recorder.endSpan(ctx);

        final JsonObject entry = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject();
        assertEquals("web", entry.getAsJsonObject("meta").get("service").getAsString(), "the span as it arrived");
        final JsonObject pairing = entry.getAsJsonObject("run").getAsJsonArray("outputs").get(0).getAsJsonObject();
        final JsonObject half = pairing.getAsJsonObject("half");
        assertEquals("web-v2", half.get("service").getAsString());
        assertEquals("cart:80", half.get("peer").getAsString());
        assertEquals("an-earlier-rule", pairing.get("offeredBy").getAsString(),
            "the span sent the earlier rule's half, not this one");
    }

    @Test
    void aPairingOutputCarriesTheHalfTheSpanOffered() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("cart").kind(SpanMetadata.KIND_SERVER).traceId("t1").spanId("s2")
                             .parentSpanId("s1").build(), Span.getDefaultInstance(), Map.of(), Map.of());

        recorder.beginSpan(ctx, Map.of(), Map.of());
        recorder.step(ctx, "extractor", "buildNetworkAlias", 5, "buildNetworkAlias addr", null);
        recorder.pairing("callee", "sent", null, null);
        recorder.endSpan(ctx);

        final JsonObject half = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                    .getAsJsonObject("run").getAsJsonArray("outputs").get(0)
                                                    .getAsJsonObject().getAsJsonObject("half");
        assertEquals("t1", half.get("traceId").getAsString());
        assertEquals("s2", half.get("spanId").getAsString());
        assertEquals("s1", half.get("parentSpanId").getAsString(), "the caller span this callee half meets");
        assertEquals("cart", half.get("service").getAsString());
        assertFalse(half.has("peer"), "a callee half names no peer");
    }

    @Test
    void anInlineDslWhoseValueReadsAsSeveralLinesHasNoFileLines() {
        // One physical line, decoded to three by its escapes: the steps' DSL lines are not the file's.
        assertEquals(0, TalRuleFiles.dslFirstLine("rules:\n  - name: r\n    dsl: \"filter {\\n  abort {}\\n}\"\n", "r"));
    }

    @Test
    void aRecordNamesTheFileLineItsRuleDslStartsAtWhenTheFileIsKnown() {
        for (final int dslFirstLine : new int[] {36, 0}) {
            final TALDebugRecorderImpl recorder = recorder(Granularity.BLOCK, dslFirstLine);
            final ExecutionContext ctx = new ExecutionContext();
            ctx.init(SpanMetadata.builder().service("cart").kind(SpanMetadata.KIND_SERVER).traceId("t1").spanId("s2")
                                 .parentSpanId("s1").build(), Span.getDefaultInstance(), Map.of(), Map.of());
            recorder.beginSpan(ctx, Map.of(), Map.of());
            recorder.step(ctx, "filter", "abort", 5, "abort {}", null);
            recorder.endSpan(ctx);

            final List<ExecutionRecord> records = recorder.snapshotRecords();
            assertEquals(1, records.size());
            final Map<String, String> rule = records.get(0).getMetadata();
            assertEquals("native-segment", rule.get("ruleName"));
            if (dslFirstLine > 0) {
                assertEquals("36", rule.get("dslFirstLine"), "the line the steps' file lines count from");
                final JsonObject step = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                            .getAsJsonObject("run").getAsJsonArray("steps").get(0)
                                                            .getAsJsonObject();
                assertEquals(40, step.get("sourceLine").getAsInt());
            } else {
                assertNull(rule.get("dslFirstLine"), "a node that cannot read the file names no line");
            }
        }
    }

    @Test
    void anIfStepCarriesTheOperandsItEvaluatedAndItsResult() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("web").kind(SpanMetadata.KIND_SERVER).spanId("s1").build(),
            Span.getDefaultInstance(), Map.of(), Map.of());

        recorder.beginSpan(ctx, Map.of(), Map.of());
        recorder.operand(ctx, "meta.kind", "SERVER");
        recorder.operand(ctx, "meta.root", true);
        recorder.condition(ctx, "filter", 3, "if (meta.kind == \"SERVER\" && meta.root)", true);
        recorder.condition(ctx, "filter", 5, "if (true)", true);
        recorder.operand(ctx, "attr(\"x\")", null);
        recorder.failed(ctx, new IllegalStateException("cut short"));
        recorder.endSpan(ctx);

        final JsonArray steps = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                    .getAsJsonObject("run").getAsJsonArray("steps");
        final JsonObject first = steps.get(0).getAsJsonObject();
        final JsonArray operands = first.getAsJsonArray("operands");
        assertEquals(2, operands.size());
        assertEquals("meta.kind", operands.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("SERVER", operands.get(0).getAsJsonObject().get("value").getAsString());
        assertTrue(operands.get(1).getAsJsonObject().get("value").getAsBoolean());
        assertTrue(first.get("result").getAsBoolean());
        assertFalse(steps.get(1).getAsJsonObject().has("operands"), "a condition takes only its own operands");
        for (int i = 0; i < steps.size(); i++) {
            final JsonArray stray = steps.get(i).getAsJsonObject().getAsJsonArray("operands");
            assertTrue(stray == null || stray.size() == 2, "the operands a failure cut short belong to no step");
        }
    }

    @Test
    void aBlockCaptureRecordsNoOperands() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.BLOCK, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("web").spanId("s1").build(), Span.getDefaultInstance(), Map.of(),
            Map.of());

        recorder.beginSpan(ctx, Map.of(), Map.of());
        recorder.operand(ctx, "meta.kind", "SERVER");
        recorder.condition(ctx, "filter", 3, "if (meta.kind == \"SERVER\")", true);
        recorder.endSpan(ctx);

        assertEquals(0, onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                            .getAsJsonObject("run").getAsJsonArray("steps").size());
    }

    @Test
    void anAbortInFirstEndsThatBlockOnlyAndAFailureIsTheReason() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final SpanObject entry = span(0, SpanType.Entry);
        final SwNativeSegmentState segment = segment(entry);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment, new Object[1]);

        recorder.beginSegment(segment);
        recorder.beginSpan(ctx, null, null);
        recorder.step(ctx, "first", "abort", 3, "abort {}", null);
        recorder.beginSpan(ctx, null, null);
        recorder.step(ctx, "filter", "def", 6, "def op = span.operationName", "op0");
        recorder.endSpan(ctx);
        recorder.endSegment();
        final JsonObject afterAbort = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                          .getAsJsonObject("run");
        assertTrue(afterAbort.get("continueOn").getAsBoolean(), "the regular run went on: " + afterAbort);

        recorder.beginSegment(segment);
        recorder.beginSpan(ctx, null, null);
        recorder.step(ctx, "filter", "abort", 4, "abort {}", null);
        recorder.failed(ctx, new IllegalStateException("boom"));
        recorder.endSegment();
        final JsonObject failed = body(recorder.snapshotRecords().get(1)).getAsJsonArray("spans").get(0)
                                                                        .getAsJsonObject().getAsJsonObject("run");
        assertEquals("IllegalStateException: boom", failed.get("reason").getAsString());
    }

    @Test
    void aFailedSegmentStillListsEverySpanAndTheFailingRunsState() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final SpanObject entry = span(0, SpanType.Entry);
        final SpanObject exit = span(1, SpanType.Exit);
        final SwNativeSegmentState segment = segment(entry, exit);
        final Object[] defs = new Object[] {"svc"};
        final ExecutionContext ctx = new ExecutionContext();
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment, defs);

        recorder.beginSegment(segment);
        recorder.beginSpan(ctx, null, null);
        recorder.failed(ctx, new IllegalArgumentException("not a number"));
        recorder.endSegment();

        final JsonArray spans = onlyRecord(recorder).getAsJsonArray("spans");
        assertEquals(2, spans.size(), "every span of the segment, also the one the rule never ran on");
        final JsonObject failed = spans.get(0).getAsJsonObject().getAsJsonObject("run");
        assertEquals("svc", failed.getAsJsonObject("state").getAsJsonObject("segmentDefs").get("owner")
                                  .getAsString(), "the failing run's state is kept");
        assertFalse(spans.get(1).getAsJsonObject().has("run"), "a span the rule never ran on has no run");
    }

    @Test
    void valuesRenderAsJson() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().spanId("s1").build(), Span.getDefaultInstance(), Map.of(), Map.of());
        final JsonObject parsed = new JsonObject();
        parsed.addProperty("logic", "checkout");

        recorder.beginSpan(ctx, Map.of(), Map.of());
        recorder.step(ctx, "extractor", "def", 3, "def logicJson = parseJsonObject(...)", parsed);
        recorder.step(ctx, "extractor", "def", 4, "def ranks = ...", new int[] {50, 99});
        recorder.metric(ctx, "extractor", 5, "metrics {", "m", null, new ApdexArgument(120, "web", true), 1L);
        recorder.endSpan(ctx);

        final JsonObject run = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject()
                                                   .getAsJsonObject("run");
        final JsonArray steps = run.getAsJsonArray("steps");
        assertEquals("checkout", steps.get(0).getAsJsonObject().getAsJsonObject("value").get("logic")
                                      .getAsString(), "a parsed JSON object is JSON, not its text");
        assertEquals(99, steps.get(1).getAsJsonObject().getAsJsonArray("value").get(1).getAsInt());
        assertEquals(120, run.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("observation")
                             .get("latency").getAsInt(), "a meter observation renders its fields");
    }

    @Test
    void aProbeWithNoOpenRecordIsDropped() {
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final SpanObject entry = span(0, SpanType.Entry);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.bindNativeRun(SpanMetadata.builder().build(), entry, segment(entry), new Object[0]);

        // The session installed mid-segment: the segment's start was missed, so the segment is not recorded.
        recorder.beginSpan(ctx, null, null);
        recorder.step(ctx, "filter", "def", 2, "def x = 1", 1);
        recorder.endSpan(ctx);
        recorder.endSegment();

        assertTrue(recorder.snapshotRecords().isEmpty());
    }

    @Test
    void theRuleFileMapsTheDslLinesToItsOwn() throws Exception {
        final List<String> lines = Files.readAllLines(shippedFile("native-segment"));
        int dslKey = -1;
        for (int i = lines.indexOf("  - name: native-segment"); i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("dsl:")) {
                dslKey = i + 1;
                break;
            }
        }
        assertEquals(dslKey + 1, TalRuleFiles.dslFirstLine(String.join("\n", lines), "native-segment"),
            "the DSL of a `dsl: |` block starts on the line after the key");
        assertEquals(0, TalRuleFiles.dslFirstLine(String.join("\n", lines), "no-such-rule"));
    }

    @Test
    void theVoteOfAnAbortedRunIsNoOutput() {
        // The runner merges the vote of a run that continued only; an aborted run keeps it in its state.
        final TALDebugRecorderImpl recorder = recorder(Granularity.STATEMENT, 1);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("web").traceStorage("otlp").build(),
            Span.newBuilder().setName("GET /cart").build(), Map.of(), Map.of());
        recorder.beginSpan(ctx, Map.of(), Map.of());
        ctx.votePersist(false);
        recorder.step(ctx, "persist", "dropper", 3, "dropper {}", "drop");
        recorder.step(ctx, "filter", "abort", 4, "abort {}", null);
        recorder.endSpan(ctx);

        final JsonObject entry = onlyRecord(recorder).getAsJsonArray("spans").get(0).getAsJsonObject();
        final JsonObject run = entry.getAsJsonObject("run");
        assertEquals(0, run.getAsJsonArray("outputs").size());
        assertEquals("drop", run.getAsJsonObject("state").get("persist").getAsString());
        assertEquals("otlp", entry.getAsJsonObject("meta").get("traceStorage").getAsString());
    }

    @Test
    void onlyALiteralOrOneLineDslMapsToTheFile() {
        assertEquals(3, TalRuleFiles.dslFirstLine("rules:\n  - name: r\n    dsl: \"filter { abort {} }\"\n", "r"));
        assertEquals(0, TalRuleFiles.dslFirstLine("rules:\n  - name: r\n    dsl: >\n      filter {\n      }\n", "r"),
            "a folded value joins its lines, so the steps keep their lines within the DSL");
    }

    @Test
    void twoRulesOfOneNameInAFileAreNotDebuggable() {
        final TALHolderRegistry registry = new TALHolderRegistry();
        registry.register(binding("GENERAL", new GateHolder("filter {}")));
        registry.register(binding("MESH", new GateHolder("filter {}")));
        registry.register(binding("K8S", new GateHolder("filter {}")));

        assertNull(registry.lookup(new RuleKey(Catalog.TAL, "custom", "shared")),
            "a session would otherwise record one layer's spans against another rule's lines");
        assertEquals(3, registry.bindings().size(), "the listing still shows every rule");
    }

    // ==================== Harness ====================

    private static TALDebugRecorderImpl recorder(final Granularity granularity, final int dslFirstLine) {
        final GateHolder holder = new GateHolder("filter {}");
        holder.setMetadata(Map.of("ruleName", "native-segment", "segmentDefs", "owner"));
        return new TALDebugRecorderImpl("session", KEY, holder, new SessionLimits(10, 60_000L, granularity),
            granularity == Granularity.STATEMENT, dslFirstLine);
    }

    @Test
    void aRuleFileIsKeyedByTheNameTheLoaderGivesIt() {
        // The loader takes payments.YAML as the file payments, as it does payments.yaml.
        final TALHolderRegistry registry = new TALHolderRegistry();
        final GateHolder holder = new GateHolder("filter {}");
        registry.register(new TalStaticBindingHook.Binding("payments.YAML", "payments", "current", "otlp", "GENERAL",
            TalStaticBindingHook.LIST_TAL_FILES, -1, 1, new String[0], holder));

        assertEquals(holder, registry.lookup(new RuleKey(Catalog.TAL, "payments", "current")));
    }

    private static TalStaticBindingHook.Binding binding(final String layer, final GateHolder holder) {
        return new TalStaticBindingHook.Binding("custom.yaml", "custom", "shared", "otlp", layer,
            TalStaticBindingHook.LIST_TAL_FILES, -1, 1, new String[0], holder);
    }

    private static JsonObject onlyRecord(final TALDebugRecorderImpl recorder) {
        final List<ExecutionRecord> records = recorder.snapshotRecords();
        assertEquals(1, records.size());
        return body(records.get(0));
    }

    private static JsonObject body(final ExecutionRecord record) {
        return JsonParser.parseString(record.getBodyJson()).getAsJsonObject();
    }

    private static SpanObject span(final int spanId, final SpanType type) {
        return SpanObject.newBuilder().setSpanId(spanId).setParentSpanId(spanId == 0 ? -1 : 0)
                         .setOperationName("op" + spanId).setSpanType(type).build();
    }

    private static SwNativeSegmentState segment(final SpanObject... spans) {
        final SegmentObject.Builder builder = SegmentObject.newBuilder().setService("svc").setTraceId("t1")
                                                           .setTraceSegmentId("seg1");
        for (final SpanObject span : spans) {
            builder.addSpans(span);
        }
        final SwNativeSegmentState state = new SwNativeSegmentState(builder.build());
        state.setLatency(42);
        return state;
    }

    private static Path shippedFile(final String name) {
        final String relative = "oap-server/server-starter/src/main/resources/tal/" + name + ".yaml";
        Path dir = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(relative))) {
                return dir.resolve(relative);
            }
        }
        return fail("could not locate " + relative);
    }
}
