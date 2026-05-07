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
 * Probe surface generated OAL bytecode calls into. Same shape as
 * {@code MALDebug} / {@code LALDebug}: each probe takes the per-rule
 * {@link GateHolder} as its first argument and fans the typed payload to
 * every recorder bound on the holder.
 *
 * <p>OAL gates are per-metric — each {@code do<Metric>} on a dispatcher
 * has its own {@code public final GateHolder debug_<metricName>} field
 * (see {@link DebugHolderProvider#debugHolder(String)}). A debug session
 * targets one metric and the other metrics on the same dispatcher stay
 * silent.
 *
 * <p>Every probe carries the metric's source-line in {@code core.oal} so
 * the captured records can render an in-source pointer alongside the
 * verbatim {@code sourceText}.
 */
public final class OALDebug {

    private OALDebug() {
    }

    /** Functional dispatcher used by every probe to forward to one recorder. */
    @FunctionalInterface
    private interface Dispatch {
        void to(OALDebugRecorder recorder);
    }

    public static void captureSource(final GateHolder holder, final String sourceText,
                                     final int sourceLine, final ISource source) {
        fanOut(holder, r -> r.appendSource(sourceText, sourceLine, source));
    }

    public static void captureFilter(final GateHolder holder, final String sourceText,
                                     final int sourceLine, final ISource source,
                                     final boolean kept) {
        fanOut(holder, r -> r.appendFilter(sourceText, sourceLine, source, kept));
    }

    public static void captureBuild(final GateHolder holder, final String metric,
                                    final int sourceLine, final Metrics built) {
        fanOut(holder, r -> r.appendBuild(metric, sourceLine, built));
    }

    public static void captureAggregation(final GateHolder holder, final String metric,
                                          final String aggregationText, final int sourceLine,
                                          final Metrics afterEntryFunction) {
        fanOut(holder, r -> r.appendAggregation(metric, aggregationText, sourceLine, afterEntryFunction));
    }

    public static void captureEmit(final GateHolder holder, final String metric,
                                   final int sourceLine, final Metrics readyForL1) {
        fanOut(holder, r -> r.appendEmit(metric, sourceLine, readyForL1));
    }

    private static void fanOut(final GateHolder holder, final Dispatch dispatch) {
        final DebugRecorder[] snapshot = holder.getRecorders();
        for (int i = 0; i < snapshot.length; i++) {
            final DebugRecorder recorder = snapshot[i];
            if (recorder.isCaptured()) {
                continue;
            }
            dispatch.to((OALDebugRecorder) recorder);
        }
    }
}
