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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps a character offset in a formatted MAL expression back to the YAML line it was written on.
 *
 * <p>A rule's compiled expression is not one source location. {@code MetricConvert.formatExp}
 * produces {@code "(" + injectExpPrefix(exp, expPrefix) + ")." + expSuffix}, so a single generated
 * method can contain stages authored on three different lines of the rule file:
 *
 * <pre>
 *   vm.yaml:32   expSuffix: service(['host'], Layer.OS_LINUX)
 *   vm.yaml:38   exp:       (node_cpu * 100).sum(['host']).rate('PT1M')
 *
 *   formatted:   ((node_cpu * 100).sum(['host']).rate('PT1M')).service(['host'], ...)
 *                 └──────────────── line 38 ─────────────────┘  └──── line 32 ────┘
 * </pre>
 *
 * <p>Without this, every stage would report the rule's line, which is right for the {@code exp:}
 * body and wrong for everything spliced around it — and wrong in a way that looks plausible,
 * because the reported line does exist in the file.
 *
 * <p>Segments are half-open {@code [start, end)} over the formatted text and are searched in
 * order, so the {@code expPrefix} spans (which sit INSIDE the {@code exp} span) must be added
 * first. An offset matching nothing yields {@link MalSourceRef#UNRESOLVED}.
 */
public final class MalSourceMap {

    /** Empty map — every lookup is unresolved. For callers with no line information. */
    public static final MalSourceMap EMPTY =
        new MalSourceMap(Collections.emptyList(), MalSourceRef.UNRESOLVED);

    private final List<Segment> segments;
    /**
     * The rule's own {@code exp:} line. Carried here so callers that need a fallback for a
     * capture with no offset of its own don't have to thread a second parameter alongside the
     * map through the whole compile chain.
     */
    private final int expYamlLine;

    private MalSourceMap(final List<Segment> segments, final int expYamlLine) {
        this.segments = segments;
        this.expYamlLine = expYamlLine;
    }

    public int getExpYamlLine() {
        return expYamlLine > 0 ? expYamlLine : MalSourceRef.UNRESOLVED;
    }

    private static final class Segment {
        private final int start;
        private final int end;
        private final int yamlLine;

        Segment(final int start, final int end, final int yamlLine) {
            this.start = start;
            this.end = end;
            this.yamlLine = yamlLine;
        }
    }

    /**
     * Build the map for one rule.
     *
     * @param injection     result of splicing {@code expPrefix} into {@code exp}
     * @param hasExpSuffix  whether {@code formatExp} appended {@code ").<expSuffix>"}
     * @param expLine       YAML line of the rule's {@code exp:} key
     * @param expPrefixLine YAML line of the file-level {@code expPrefix:} key
     * @param expSuffixLine YAML line of the file-level {@code expSuffix:} key
     * @return a map over the formatted expression's offsets
     */
    public static MalSourceMap of(final MALScriptParser.PrefixInjection injection,
                                  final boolean hasExpSuffix,
                                  final int expLine,
                                  final int expPrefixLine,
                                  final int expSuffixLine) {
        if (injection == null) {
            return EMPTY;
        }
        // formatExp wraps the injected text in "(...)" when a suffix follows, shifting every
        // offset right by one. Keeping that arithmetic here, next to the layout it mirrors, is
        // why this class takes the injection rather than the finished string.
        final int shift = hasExpSuffix ? 1 : 0;
        final int injectedLength = injection.getText().length();

        final List<Segment> segments = new ArrayList<>();
        // expPrefix spans first: they are nested inside the exp span and must win the lookup.
        for (final int[] range : injection.getPrefixRanges()) {
            segments.add(new Segment(range[0] + shift, range[1] + shift, expPrefixLine));
        }
        segments.add(new Segment(shift, shift + injectedLength, expLine));
        if (hasExpSuffix) {
            // Everything past the closing ")." belongs to the file-level suffix.
            segments.add(new Segment(shift + injectedLength + 2, Integer.MAX_VALUE, expSuffixLine));
        }
        return new MalSourceMap(segments, expLine);
    }

    /**
     * @param offset character offset in the formatted expression, or negative when unknown
     * @return the 1-based YAML line, or {@link MalSourceRef#UNRESOLVED}
     */
    public int yamlLineOf(final int offset) {
        if (offset < 0) {
            return MalSourceRef.UNRESOLVED;
        }
        for (final Segment segment : segments) {
            if (offset >= segment.start && offset < segment.end) {
                return segment.yamlLine > 0 ? segment.yamlLine : MalSourceRef.UNRESOLVED;
            }
        }
        return MalSourceRef.UNRESOLVED;
    }
}
