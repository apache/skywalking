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
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;

/**
 * One debug session's recorder for a TAL rule. A record follows the object the runner passes through the rule: one
 * OTLP span or one native segment. Every record lists spans, each with its run: the statements it executed, the
 * outputs that left the rule and the state at its end.
 *
 * <p>The runners open and close records; the probes the generator emits append steps to the span the record is on.
 * A probe that arrives while no record is open on its thread, as when a session installs mid-segment, is dropped.
 */
public interface TALDebugRecorder extends DebugRecorder {

    /**
     * Open a native record: the segment and the values the runner derived from it before any rule ran.
     */
    void beginSegment(SwNativeSegmentState segment);

    /**
     * Start a span's run. For OTLP this opens the record; for native it adds the span to the open segment record,
     * or does nothing when the span is already there, as span 0 is after its {@code first {}} run.
     *
     * @param resource the OTLP resource attributes; null for native
     * @param scope    the OTLP instrumentation scope attributes; null for native
     */
    void beginSpan(ExecutionContext ctx, Map<String, String> resource, Map<String, String> scope);

    /**
     * A native {@code build {}} block is about to run, after the segment's last span: its steps go to the span whose
     * run queued it.
     */
    void beginBuild(Object span, int index);

    /**
     * The span's run ended. For OTLP this publishes the record, after {@link #pairing} reported what the run sent.
     */
    void endSpan(ExecutionContext ctx);

    /**
     * The native segment's build phase ended; publishes the record.
     */
    void endSegment();

    /**
     * The rule threw on this run; the run stops here.
     */
    void failed(ExecutionContext ctx, Throwable failure);

    /**
     * What became of a network-alias half this OTLP run asked for, decided once every rule of the span ran.
     *
     * @param role      {@code caller} for {@code prepareNetworkAlias}, {@code callee} for {@code buildNetworkAlias}
     * @param outcome   the {@code PairingOutcome} code of the half the span offered, which is an earlier rule's when
     *                  {@code offeredBy} names one
     * @param peer      the peer this run named for a caller half; null for a callee half
     * @param offeredBy the earlier rule whose half the span offered instead of this run's; null when it was this one
     */
    void pairing(String role, String outcome, String peer, String offeredBy);

    /**
     * One executed statement.
     *
     * @param stage the block it sits in: {@code first}, {@code filter}, {@code extractor}, {@code persist} or
     *              {@code build}
     * @param kind  the statement's kind, such as {@code def}, {@code tag} or {@code block}
     * @param line  its first line, 1-based within the rule's DSL
     * @param value what the statement produced; null for a statement that produces nothing
     */
    void step(ExecutionContext ctx, String stage, String kind, int line, String text, Object value);

    /**
     * An {@code if} evaluated an operand of its condition, before {@link #condition} reports the condition.
     */
    default void operand(ExecutionContext ctx, String text, Object value) {
    }

    /**
     * An {@code if} evaluated its condition.
     */
    void condition(ExecutionContext ctx, String stage, int line, String text, boolean result);

    /**
     * A {@code source <Scope> {}} block is sent.
     */
    void source(ExecutionContext ctx, String stage, int line, String text, ISource source);

    /**
     * A {@code metrics {}} block sent its value.
     */
    void metric(ExecutionContext ctx, String stage, int line, String text, String metric, MeterEntity entity,
                Object observation, long timestamp);

    /**
     * A {@code metrics {}} block sent nothing.
     *
     * @param reason why, such as the exception it failed with
     */
    void metricSkipped(ExecutionContext ctx, String stage, int line, String text, String metric, String reason);
}
