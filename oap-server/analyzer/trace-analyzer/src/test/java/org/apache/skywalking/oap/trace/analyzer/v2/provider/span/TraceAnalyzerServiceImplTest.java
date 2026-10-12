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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TestSpanInput;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TestSpanSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * How several rules' verdicts on one span are merged.
 *
 * <p>This is the logic that decides whether a span reaches storage, and it is the one place where one rule can
 * affect another. The rules here are stubs rather than compiled DSL: what is under test is the merge, not the
 * codegen that the compile tests cover.
 */
class TraceAnalyzerServiceImplTest {
    private SpanFilterListener.Factory factory;
    private TraceAnalyzerServiceImpl service;
    private final List<PairingFact> pairingFacts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        factory = mock(SpanFilterListener.Factory.class);
        Mockito.when(factory.hasRules()).thenReturn(true);
        final SpanPairing pairing = new SpanPairing();
        pairing.setTransport(pairingFacts::add);
        service = new TraceAnalyzerServiceImpl(mock(ModuleManager.class), factory, new KnownServices(), pairing,
            new SeenSpans(0, 1));
    }

    @Test
    void withNoRulesInstalledASpanCostsNothing() {
        // talFiles empty: the operator wants the traces stored and nothing analyzed, so the pairing must not run
        // either, or a cluster would still route a share of every SERVER span to its trace owner for nothing.
        Mockito.when(factory.hasRules()).thenReturn(false);
        final SpanAnalysisResult result = analyze();
        assertTrue(result.isPersist());
        assertTrue(pairingFacts.isEmpty(), "no pairing fact without rules: " + pairingFacts);
        Mockito.verify(factory, Mockito.never()).rulesFor(any(), any());
    }

    @Test
    void aSpanOnAnUnknownLayerIsNotAnalyzed() {
        // Layer.nameOf would file it under UNDEFINED, where no rule runs but the pairing would still register
        // the callee as a normal service; an unregistered name is reported and the span left alone instead.
        final int[] runs = {0};
        given(rule(ctx -> runs[0]++));
        final SpanMetadata meta = SpanMetadata.builder()
                                             .service("frontend")
                                             .kind(SpanMetadata.KIND_SERVER)
                                             .layer("OTLP_SERVCE")
                                             .traceId("t1")
                                             .spanId("s2")
                                             .parentSpanId("s1")
                                             .timestamp(1_700_000_000_500L)
                                             .build();
        final SpanAnalysisResult result =
            service.doAnalysis(meta, new TestSpanInput(), Collections.emptyMap(), Collections.emptyMap());
        assertEquals(0, runs[0], "no rule sees a span whose layer is not registered");
        assertTrue(pairingFacts.isEmpty(), "and the pairing is not told about it either: " + pairingFacts);
        assertTrue(result.isPersist(), "the span keeps the receiver's default fate");
        Mockito.verify(factory, Mockito.never()).rulesFor(any(), any());

        // The same span under a registered layer runs the rules and pairs.
        analyze();
        assertEquals(1, runs[0]);
    }

    @Test
    void theAttributeIndexIsBuiltOnceForAllRulesOfASpan() {
        final int[] builds = {0};
        final TestSpanSourceTypeProvider counting = new TestSpanSourceTypeProvider() {
            @Override
            public Map<String, String> spanAttributes(final Object input) {
                builds[0]++;
                return super.spanAttributes(input);
            }
        };
        final String[] seen = new String[2];
        given(rule(ctx -> seen[0] = ctx.spanAttributes().get("http.route"), counting),
              rule(ctx -> seen[1] = ctx.spanAttributes().get("http.route"), counting));
        service.doAnalysis(spanMetadata(), new TestSpanInput().attribute("http.route", "/cart"),
                           Collections.emptyMap(), Collections.emptyMap());
        assertEquals(1, builds[0], "the second rule reuses the index the first one built");
        assertEquals("/cart", seen[0]);
        assertEquals("/cart", seen[1]);
    }

    @Test
    void aRetriedSpanRunsTheRulesOnceInsideTheDedupWindow() {
        final int[] runs = {0};
        given(rule(ctx -> runs[0]++));
        final TraceAnalyzerServiceImpl deduping = new TraceAnalyzerServiceImpl(
            mock(ModuleManager.class), factory, new KnownServices(), new SpanPairing(), new SeenSpans(60, 1000));
        deduping.doAnalysis(spanMetadata(), new TestSpanInput(), Collections.emptyMap(), Collections.emptyMap());
        final SpanAnalysisResult retry = deduping.doAnalysis(
            spanMetadata(), new TestSpanInput(), Collections.emptyMap(), Collections.emptyMap());
        assertEquals(1, runs[0], "the retried batch does not reach the rules");
        assertTrue(retry.isPersist(), "a skipped span keeps the receiver's default fate");

        // Off by default: the same two arrivals run the rules twice.
        analyze();
        analyze();
        assertEquals(3, runs[0]);
    }

    @Test
    void noRulesLeavesTheSpanAlone() {
        given();
        final SpanAnalysisResult result = analyze();
        assertTrue(result.isPersist());
        assertFalse(result.isEnforced());
        assertTrue(result.getAdditionalTags().isEmpty());
    }

    @Test
    void oneDropVoteDropsTheSpan() {
        given(rule(ctx -> ctx.votePersist(true)), rule(ctx -> ctx.votePersist(false)));
        assertFalse(analyze().isPersist(), "a single drop vote is enough");
    }

    @Test
    void anAbortedRuleDoesNotVote() {
        // The rule would have voted to drop, but it aborted first, so its vote does not count.
        given(rule(ctx -> {
            ctx.votePersist(false);
            ctx.abort();
        }));
        assertTrue(analyze().isPersist(), "a rule that aborted did not vote");
    }

    @Test
    void aDropBeatsAnEnforcer() {
        // The design is explicit: an enforcer {} overrides the samplers in its own rule, not another rule's veto.
        given(rule(ExecutionContext::enforce), rule(ctx -> ctx.votePersist(false)));
        final SpanAnalysisResult result = analyze();
        assertFalse(result.isPersist());
        assertFalse(result.isEnforced(), "enforced is only meaningful on a span that is persisted");
    }

    @Test
    void anEnforcerSurvivesWhenNothingDrops() {
        given(rule(ctx -> ctx.votePersist(true)), rule(ExecutionContext::enforce));
        final SpanAnalysisResult result = analyze();
        assertTrue(result.isPersist());
        assertTrue(result.isEnforced());
    }

    @Test
    void tagsMergeLastWriterWins() {
        given(rule(ctx -> ctx.addTag("k", "first")),
              rule(ctx -> {
                  ctx.addTag("k", "second");
                  ctx.addTag("other", "kept");
              }));
        assertEquals(Map.of("k", "second", "other", "kept"), analyze().getAdditionalTags());
    }

    @Test
    void oneRulesOverwriteIsInvisibleToTheNext() {
        // Each rule gets its own metadata copy; without that, rule order would change rule two's entities.
        final String[] seenBySecond = new String[1];
        given(rule(ctx -> ctx.getMeta().setService("renamed")),
              rule(ctx -> seenBySecond[0] = ctx.getMeta().getService()));
        analyze();
        assertEquals("frontend", seenBySecond[0], "the second rule saw the first rule's overwrite");
    }

    @Test
    void aFailingRuleDoesNotCostTheSpan() {
        given(rule(ctx -> {
                  throw new IllegalStateException("boom");
              }),
              rule(ctx -> ctx.addTag("still", "ran")));
        final SpanAnalysisResult result = analyze();
        assertTrue(result.isPersist(), "a broken rule leaves storage as it was");
        assertEquals(Map.of("still", "ran"), result.getAdditionalTags(),
            "the rules after it still run");
    }

    // ==================== Harness ====================

    private SpanAnalysisResult analyze() {
        return service.doAnalysis(
            spanMetadata(), new TestSpanInput(), Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Install the rules this span should see.
     */
    private void given(final SpanFilterListener.RuleBinding... bindings) {
        Mockito.when(factory.rulesFor(any(), any())).thenReturn(List.of(bindings));
    }

    /**
     * A rule that does whatever the body says to its context, standing in for compiled DSL.
     */
    private static SpanFilterListener.RuleBinding rule(final Consumer<ExecutionContext> body) {
        return rule(body, new TestSpanSourceTypeProvider());
    }

    private static SpanFilterListener.RuleBinding rule(final Consumer<ExecutionContext> body,
                                                       final TestSpanSourceTypeProvider provider) {
        final TalDSL dsl = mock(TalDSL.class);
        Mockito.doAnswer(invocation -> {
            body.accept(invocation.getArgument(0));
            return null;
        }).when(dsl).evaluate(any());
        return new SpanFilterListener.RuleBinding(dsl, provider);
    }

    private static SpanMetadata spanMetadata() {
        return SpanMetadata.builder()
                           .service("frontend")
                           .endpoint("HTTP GET")
                           .kind(SpanMetadata.KIND_SERVER)
                           .statusCode(SpanMetadata.STATUS_UNSET)
                           .layer(Layer.OTLP_SERVICE.name())
                           .latency(110)
                           .timestamp(1_700_000_000_500L)
                           .traceId("t1")
                           .spanId("s1")
                           .build();
    }
}
