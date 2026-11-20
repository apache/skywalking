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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Each rule a log runs starts from the layer the log arrived with: a rule's {@code layer} statement writes only that
 * rule's copy of the metadata, never the layer another rule saves the log under.
 */
class LogFilterListenerPerRuleMetadataTest {
    private final List<String> seen = new ArrayList<>();

    @Test
    void aValidLayerOfOneRuleIsNotSeenByTheNext() {
        final LogMetadata metadata = log("GENERAL");

        run(metadata, false, decides("MESH"), records());

        assertEquals(List.of("GENERAL"), seen);
        assertEquals("GENERAL", metadata.getLayer(), "the log's own metadata is not written by a rule");
    }

    @Test
    void anInvalidLayerOfOneRuleIsNotSeenByTheNext() {
        run(log("GENERAL"), false, decides("bogus"), records(), decides(null), records(), decides(""), records());

        assertEquals(List.of("GENERAL", "GENERAL", "GENERAL"), seen);
    }

    @Test
    void theRuleThatDecidedALayerSeesIt() {
        run(log("GENERAL"), false, decidesAndRecords("MESH"), records());

        assertEquals(List.of("MESH", "GENERAL"), seen);
    }

    @Test
    void anAutoRulesLayerIsNotSeenByAnotherAutoRule() {
        run(log(""), true, decides("IOS"), records());

        assertEquals(List.of(""), seen, "the other auto rule must still decide a layer of its own");
    }

    @Test
    void theFallbackRulesDoNotSeeALayerAnAbortedAutoRuleSet() {
        final LogMetadata metadata = log("");
        final DSL setsAndAborts = mock(DSL.class);
        doAnswer(inv -> {
            ((ExecutionContext) inv.getArgument(0)).layer("IOS").abort();
            return null;
        }).when(setsAndAborts).evaluate(any());

        // LogAnalyzer runs the auto pass and, as no rule claimed the log, the fallback pass on the same metadata.
        final LogFilterListener auto = run(metadata, true, setsAndAborts);
        run(metadata, true, records());

        assertFalse(auto.claimed());
        assertEquals(List.of(""), seen, "the fallback rule starts from the layer the log arrived with");
        assertEquals("", metadata.getLayer());
    }

    private LogFilterListener run(final LogMetadata metadata, final boolean autoMode, final DSL... rules) {
        final LogFilterListener listener = new LogFilterListener(Arrays.asList(rules), autoMode, null);
        listener.parse(metadata, null);
        listener.build();
        return listener;
    }

    private static LogMetadata log(final String layer) {
        return LogMetadata.builder().service("svc").layer(layer).timestamp(1L).build();
    }

    /**
     * A rule whose {@code layer} statement runs, as the generated code calls it.
     */
    private static DSL decides(final String layer) {
        final DSL rule = mock(DSL.class);
        doAnswer(inv -> {
            ((ExecutionContext) inv.getArgument(0)).layer(layer);
            return null;
        }).when(rule).evaluate(any());
        return rule;
    }

    private DSL decidesAndRecords(final String layer) {
        final DSL rule = mock(DSL.class);
        doAnswer(inv -> {
            final ExecutionContext ctx = inv.getArgument(0);
            ctx.layer(layer);
            seen.add(ctx.metadata().getLayer());
            return null;
        }).when(rule).evaluate(any());
        return rule;
    }

    /**
     * A rule without a {@code layer} statement, recording the layer its sink would save the log under.
     */
    private DSL records() {
        final DSL rule = mock(DSL.class);
        doAnswer(inv -> {
            seen.add(((ExecutionContext) inv.getArgument(0)).metadata().getLayer());
            return null;
        }).when(rule).evaluate(any());
        return rule;
    }
}
