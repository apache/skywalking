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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug;

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.debug.DebugRecorder;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;

/**
 * Probe surface the generated TAL classes and the TAL runners call. Every method takes the rule's {@link GateHolder}
 * and fans the call out to each recorder bound on it, the way {@code LALDebug} does for LAL. Callers check
 * {@link GateHolder#isGateOn()} first; the generated classes read it once per method into a local.
 *
 * <p>The {@code step} overloads exist because the generated code has no autoboxing: a primitive value reaches the
 * recorder through the overload of its own type.
 */
public final class TALDebug {

    /** FQCN the generated code calls, which has no imports. */
    public static final String FQCN = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TALDebug";

    private TALDebug() {
    }

    @FunctionalInterface
    private interface Dispatch {
        void to(TALDebugRecorder recorder);
    }

    public static void beginSegment(final GateHolder holder, final SwNativeSegmentState segment) {
        fanOut(holder, r -> r.beginSegment(segment));
    }

    public static void beginSpan(final GateHolder holder, final ExecutionContext ctx,
                                 final Map<String, String> resource, final Map<String, String> scope) {
        fanOut(holder, r -> r.beginSpan(ctx, resource, scope));
    }

    public static void beginBuild(final GateHolder holder, final Object span, final int index) {
        fanOut(holder, r -> r.beginBuild(span, index));
    }

    public static void endSpan(final GateHolder holder, final ExecutionContext ctx) {
        fanOut(holder, r -> r.endSpan(ctx));
    }

    public static void endSegment(final GateHolder holder) {
        fanOut(holder, TALDebugRecorder::endSegment);
    }

    public static void failed(final GateHolder holder, final ExecutionContext ctx, final Throwable failure) {
        fanOut(holder, r -> r.failed(ctx, failure));
    }

    public static void pairing(final GateHolder holder, final String role, final String outcome,
                               final String peer, final String offeredBy) {
        fanOut(holder, r -> r.pairing(role, outcome, peer, offeredBy));
    }

    public static void step(final GateHolder holder, final ExecutionContext ctx, final String stage,
                            final String kind, final int line, final String text, final Object value) {
        fanOut(holder, r -> r.step(ctx, stage, kind, line, text, value));
    }

    public static void step(final GateHolder holder, final ExecutionContext ctx, final String stage,
                            final String kind, final int line, final String text, final long value) {
        step(holder, ctx, stage, kind, line, text, (Object) value);
    }

    public static void step(final GateHolder holder, final ExecutionContext ctx, final String stage,
                            final String kind, final int line, final String text, final int value) {
        step(holder, ctx, stage, kind, line, text, (Object) value);
    }

    public static void step(final GateHolder holder, final ExecutionContext ctx, final String stage,
                            final String kind, final int line, final String text, final double value) {
        step(holder, ctx, stage, kind, line, text, (Object) value);
    }

    public static void step(final GateHolder holder, final ExecutionContext ctx, final String stage,
                            final String kind, final int line, final String text, final boolean value) {
        step(holder, ctx, stage, kind, line, text, (Object) value);
    }

    /**
     * An operand of an {@code if} condition, returned as it is so the condition goes on with it; recorded when the
     * run's gate is on. The generated code passes its gate rather than checking it, as the operand is an expression.
     */
    public static Object operand(final boolean gate, final GateHolder holder, final ExecutionContext ctx,
                                 final String text, final Object value) {
        if (gate) {
            fanOut(holder, r -> r.operand(ctx, text, value));
        }
        return value;
    }

    public static boolean operand(final boolean gate, final GateHolder holder, final ExecutionContext ctx,
                                  final String text, final boolean value) {
        if (gate) {
            fanOut(holder, r -> r.operand(ctx, text, value));
        }
        return value;
    }

    public static void condition(final GateHolder holder, final ExecutionContext ctx, final String stage,
                                 final int line, final String text, final boolean result) {
        fanOut(holder, r -> r.condition(ctx, stage, line, text, result));
    }

    public static void source(final GateHolder holder, final ExecutionContext ctx, final String stage,
                              final int line, final String text, final ISource source) {
        fanOut(holder, r -> r.source(ctx, stage, line, text, source));
    }

    public static void metric(final GateHolder holder, final ExecutionContext ctx, final String stage,
                              final int line, final String text, final String metric, final MeterEntity entity,
                              final Object observation, final long timestamp) {
        fanOut(holder, r -> r.metric(ctx, stage, line, text, metric, entity, observation, timestamp));
    }

    public static void metricSkipped(final GateHolder holder, final ExecutionContext ctx, final String stage,
                                     final int line, final String text, final String metric, final Object reason) {
        final String why = reason instanceof Throwable
            ? reason.getClass().getSimpleName() + ": " + ((Throwable) reason).getMessage()
            : String.valueOf(reason);
        fanOut(holder, r -> r.metricSkipped(ctx, stage, line, text, metric, why));
    }

    private static void fanOut(final GateHolder holder, final Dispatch dispatch) {
        if (holder == null) {
            return;
        }
        final DebugRecorder[] snapshot = holder.getRecorders();
        for (int i = 0; i < snapshot.length; i++) {
            final DebugRecorder recorder = snapshot[i];
            if (recorder.isCaptured() || !(recorder instanceof TALDebugRecorder)) {
                continue;
            }
            dispatch.to((TALDebugRecorder) recorder);
        }
    }
}
