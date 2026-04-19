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

package org.apache.skywalking.oap.server.core.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link TraceQueryService#sortSpans(List)} produces correct parent-child
 * ordering after the O(N^2) to O(N) refactor.
 */
class TraceQueryServiceTest {

    private static final String SEG_A = "segA";
    private static final String SEG_B = "segB";

    @Test
    void sortSpans_emptyInput_returnsEmptyList() {
        List<Span> result = TraceQueryService.sortSpans(Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    void sortSpans_singleRootSpan_marksAsRoot() {
        Span root = span(SEG_A, 0, -1, 100L);

        List<Span> result = TraceQueryService.sortSpans(Collections.singletonList(root));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRoot()).isTrue();
        assertThat(result.get(0).getSegmentSpanId()).isEqualTo(root.getSegmentSpanId());
    }

    @Test
    void sortSpans_linearChain_returnsParentThenChildren() {
        Span root = span(SEG_A, 0, -1, 100L);
        Span child1 = span(SEG_A, 1, 0, 200L);
        Span child2 = span(SEG_A, 2, 1, 300L);

        // Deliberately input in non-DFS order to ensure sort reorders correctly.
        List<Span> result = TraceQueryService.sortSpans(new ArrayList<>(Arrays.asList(child2, root, child1)));

        assertThat(result).extracting(Span::getSegmentSpanId)
                          .containsExactly(root.getSegmentSpanId(),
                                           child1.getSegmentSpanId(),
                                           child2.getSegmentSpanId());
        assertThat(result.get(0).isRoot()).isTrue();
    }

    @Test
    void sortSpans_multipleRootsSortedByStartTime() {
        Span laterRoot = span(SEG_A, 0, -1, 500L);
        Span earlierRoot = span(SEG_B, 0, -1, 100L);

        List<Span> result = TraceQueryService.sortSpans(new ArrayList<>(Arrays.asList(laterRoot, earlierRoot)));

        assertThat(result).extracting(Span::getSegmentSpanId)
                          .containsExactly(earlierRoot.getSegmentSpanId(),
                                           laterRoot.getSegmentSpanId());
        assertThat(result).allMatch(Span::isRoot);
    }

    @Test
    void sortSpans_multipleChildrenOfSameParent_preservesInputOrder() {
        Span root = span(SEG_A, 0, -1, 100L);
        Span childA = span(SEG_A, 1, 0, 200L);
        Span childB = span(SEG_A, 2, 0, 200L);
        Span childC = span(SEG_A, 3, 0, 200L);

        List<Span> result = TraceQueryService.sortSpans(new ArrayList<>(Arrays.asList(root, childA, childB, childC)));

        assertThat(result).extracting(Span::getSegmentSpanId)
                          .containsExactly(root.getSegmentSpanId(),
                                           childA.getSegmentSpanId(),
                                           childB.getSegmentSpanId(),
                                           childC.getSegmentSpanId());
    }

    @Test
    void sortSpans_crossSegmentParent_treatsReferenceParentCorrectly() {
        // segA root + a span in segB whose parent points at the span in segA (cross-segment ref).
        Span rootA = span(SEG_A, 0, -1, 100L);
        Span childAcrossSegment = spanWithExplicitParent(SEG_B, 0, rootA.getSegmentSpanId(), 200L);

        List<Span> result = TraceQueryService.sortSpans(new ArrayList<>(Arrays.asList(rootA, childAcrossSegment)));

        assertThat(result).extracting(Span::getSegmentSpanId)
                          .containsExactly(rootA.getSegmentSpanId(),
                                           childAcrossSegment.getSegmentSpanId());
        assertThat(result.get(0).isRoot()).isTrue();
        assertThat(result.get(1).isRoot()).isFalse();
    }

    @Test
    void sortSpans_orphanedSpan_treatedAsRoot() {
        // Parent segmentSpanId does not exist in the list (segment lost or sampled).
        Span orphan = spanWithExplicitParent(SEG_A, 5,
                                             "missing-segment" + Const.SEGMENT_SPAN_SPLIT + "99",
                                             150L);

        List<Span> result = TraceQueryService.sortSpans(Collections.singletonList(orphan));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRoot()).isTrue();
    }

    @Test
    void sortSpans_siblingSubtrees_traverseDepthFirst() {
        Span root = span(SEG_A, 0, -1, 100L);
        Span childA = span(SEG_A, 1, 0, 200L);
        Span grandChildA = span(SEG_A, 2, 1, 250L);
        Span childB = span(SEG_A, 3, 0, 300L);

        List<Span> result = TraceQueryService.sortSpans(new ArrayList<>(Arrays.asList(root, childA, grandChildA, childB)));

        assertThat(result).extracting(Span::getSegmentSpanId)
                          .containsExactly(root.getSegmentSpanId(),
                                           childA.getSegmentSpanId(),
                                           grandChildA.getSegmentSpanId(),
                                           childB.getSegmentSpanId());
    }

    private Span span(String segmentId, int spanId, int parentSpanId, long startTime) {
        Span span = new Span();
        span.setSegmentId(segmentId);
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setStartTime(startTime);
        span.setSegmentSpanId(segmentId + Const.SEGMENT_SPAN_SPLIT + spanId);
        span.setSegmentParentSpanId(segmentId + Const.SEGMENT_SPAN_SPLIT + parentSpanId);
        return span;
    }

    private Span spanWithExplicitParent(String segmentId, int spanId,
                                        String explicitSegmentParentSpanId, long startTime) {
        Span span = new Span();
        span.setSegmentId(segmentId);
        span.setSpanId(spanId);
        span.setStartTime(startTime);
        span.setSegmentSpanId(segmentId + Const.SEGMENT_SPAN_SPLIT + spanId);
        span.setSegmentParentSpanId(explicitSegmentParentSpanId);
        return span;
    }
}
