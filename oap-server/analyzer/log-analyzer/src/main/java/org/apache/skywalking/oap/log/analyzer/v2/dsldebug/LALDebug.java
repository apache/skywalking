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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.dsldebug.DebugRecorder;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;

/**
 * Probe surface generated LAL bytecode calls into. Each method takes the
 * per-rule {@link GateHolder} as its first argument and fans the typed
 * payload to every recorder bound on that holder.
 *
 * <p>Symmetric with {@code MALDebug} / {@code OALDebug}. The codegen-emitted
 * call sites all use the {@code if (this.debug.isGateOn())} idiom (see
 * {@code LALCodegenHelper.emitCaptureCall}).
 */
public final class LALDebug {

    private LALDebug() {
    }

    /** Functional dispatcher used by every probe to forward to one recorder. */
    @FunctionalInterface
    private interface Dispatch {
        void to(LALDebugRecorder recorder);
    }

    public static void captureText(final GateHolder holder, final String rule,
                                   final int sourceLine, final ExecutionContext ctx) {
        fanOut(holder, r -> r.appendText(rule, sourceLine, ctx));
    }

    public static void captureParser(final GateHolder holder, final String rule,
                                     final int sourceLine, final ExecutionContext ctx) {
        fanOut(holder, r -> r.appendParser(rule, sourceLine, ctx));
    }

    public static void captureExtractor(final GateHolder holder, final String rule,
                                        final int sourceLine, final ExecutionContext ctx) {
        fanOut(holder, r -> r.appendExtractor(rule, sourceLine, ctx));
    }

    public static void captureLine(final GateHolder holder, final String rule,
                                   final int sourceLine, final String sourceText,
                                   final ExecutionContext ctx) {
        fanOut(holder, r -> r.appendLine(rule, sourceLine, sourceText, ctx));
    }

    public static void captureOutputRecord(final GateHolder holder, final String rule,
                                           final int sourceLine, final ExecutionContext ctx,
                                           final String outputClass) {
        fanOut(holder, r -> r.appendOutputRecord(rule, sourceLine, ctx, outputClass));
    }

    public static void captureOutputMetric(final GateHolder holder, final String rule,
                                           final int sourceLine, final ExecutionContext ctx,
                                           final SampleFamily family) {
        fanOut(holder, r -> r.appendOutputMetric(rule, sourceLine, ctx, family));
    }

    private static void fanOut(final GateHolder holder, final Dispatch dispatch) {
        final DebugRecorder[] snapshot = holder.getRecorders();
        for (int i = 0; i < snapshot.length; i++) {
            final DebugRecorder recorder = snapshot[i];
            if (recorder.isCaptured()) {
                continue;
            }
            dispatch.to((LALDebugRecorder) recorder);
        }
    }
}
