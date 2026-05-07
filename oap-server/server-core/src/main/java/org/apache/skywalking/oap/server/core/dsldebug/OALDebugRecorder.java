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

package org.apache.skywalking.oap.server.core.dsldebug;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.source.ISource;

/**
 * Per-session recorder for OAL rules. OAL gates are per-metric (one
 * {@code GateHolder} per generated {@code do<Metric>}), so a session
 * captures only its bound metric's pipeline.
 *
 * <p>Every probe carries the metric's 1-based source line in
 * {@code core.oal} so the captured records carry an in-source pointer
 * alongside the verbatim {@code sourceText} (the ANTLR Interval slice).
 */
public interface OALDebugRecorder extends DebugRecorder {

    /** Source row entering the dispatcher. {@code source} is the {@link ISource} value. */
    void appendSource(String sourceText, int sourceLine, ISource source);

    /**
     * Filter clause result. {@code sourceText} is the verbatim filter
     * (e.g. {@code ".filter(detectPoint == DetectPoint.SERVER)"});
     * {@code kept} is true when the source survived (rule continues),
     * false when it was dropped.
     */
    void appendFilter(String sourceText, int sourceLine, ISource source, boolean kept);

    /** Per-metric build: typed Metrics object freshly seeded from the source. */
    void appendBuild(String metric, int sourceLine, Metrics built);

    /**
     * Per-metric aggregation: typed Metrics object after the entry function
     * ({@code longAvg}, {@code percentile2}, etc.) has run.
     */
    void appendAggregation(String metric, String aggregationText, int sourceLine,
                           Metrics afterEntryFunction);

    /** Terminal: per-metric Metrics ready for {@code MetricsStreamProcessor.in()}. */
    void appendEmit(String metric, int sourceLine, Metrics readyForL1);
}
