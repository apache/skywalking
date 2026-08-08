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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rewrite was changed from a right-to-left {@code replace} loop to a left-to-right build so
 * the injected prefix's FINAL position is recoverable. The output text must not have changed —
 * it feeds the compiler — and the reported ranges must actually contain the prefix.
 */
class MalPrefixInjectionRangesTest {

    private static final String PREFIX = "tag({tags -> tags.k = 'v'})";

    @Test
    void nullOrEmptyPrefixIsAPassThrough() {
        assertEquals("a.sum(['s'])",
            MALScriptParser.injectExpPrefix("a.sum(['s'])", null));
        assertEquals("a.sum(['s'])",
            MALScriptParser.injectExpPrefix("a.sum(['s'])", ""));
        assertTrue(MALScriptParser.injectExpPrefixTracked("a.sum(['s'])", "")
            .getPrefixRanges().isEmpty());
    }

    @Test
    void injectsAtEveryMetricReferenceNotJustTheFirst() {
        // The case the walker exists for: a second metric nested in an argument.
        final String out = MALScriptParser.injectExpPrefix("a.sum(['s']).safeDiv(b.sum(['s']))",
            PREFIX);

        assertEquals("(a." + PREFIX + ").sum(['s']).safeDiv((b." + PREFIX + ").sum(['s']))", out);
    }

    @Test
    void reportedRangesLandExactlyOnTheInjectedPrefixText() {
        final MALScriptParser.PrefixInjection injected =
            MALScriptParser.injectExpPrefixTracked("a.sum(['s']).safeDiv(b.sum(['s']))", PREFIX);

        final String text = injected.getText();
        final List<int[]> ranges = injected.getPrefixRanges();

        // One per metric reference, ascending, each slicing out exactly the prefix.
        assertEquals(2, ranges.size());
        assertTrue(ranges.get(0)[0] < ranges.get(1)[0], "ranges must be ascending");
        for (final int[] range : ranges) {
            assertEquals(PREFIX, text.substring(range[0], range[1]));
        }
    }

    @Test
    void downsamplingConstantsAreNotTreatedAsMetrics() {
        // SUM/AVG/... parse as bare identifiers but are enum values; wrapping them would
        // produce a bogus SampleFamily reference.
        final String out = MALScriptParser.injectExpPrefix("a.histogram().histogram_percentile([50])",
            PREFIX);

        assertTrue(out.startsWith("(a." + PREFIX + ")"), "metric should be wrapped: " + out);
        assertEquals(1, MALScriptParser
            .injectExpPrefixTracked("a.histogram().histogram_percentile([50])", PREFIX)
            .getPrefixRanges().size());
    }
}
