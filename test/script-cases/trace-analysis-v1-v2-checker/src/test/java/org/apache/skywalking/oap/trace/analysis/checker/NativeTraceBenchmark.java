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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Segments per second and allocation per segment through the Java analysis and the native rules, each with its own
 * collaborators and nothing recorded: the fixture mix, cold and warm alias caches, a segment with many references and
 * tags, and concurrent segments. One operation is one segment; every source a side sends goes to the benchmark
 * thread's blackhole. Logging is off, so a fixture that logs measures the analysis, not the console. Not part of
 * the test run (the class name is not a test name); run it with
 * {@code -Dtest=NativeTraceBenchmark -Dsurefire.failIfNoSpecifiedTests=false}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(2)
public class NativeTraceBenchmark {
    private static final ThreadLocal<Blackhole> SINK = new ThreadLocal<>();

    @Param({"cold", "warm"})
    public String aliasCache;

    private CheckerV1ListenerSide v1;
    private CheckerV2RuleSide v2;
    private List<SegmentObject> mix;
    private SegmentObject wide;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        // log4j's Level, beside JMH's Level the annotations use
        Configurator.setLevel("org.apache.skywalking", org.apache.logging.log4j.Level.OFF);
        NativeTraceComparisonTest.registerScopes();
        final CheckerProfile profile = CheckerProfile.builder().name("benchmark").instrumented(false).build();
        v1 = new CheckerV1ListenerSide(profile, null);
        v2 = new CheckerV2RuleSide(profile, null);
        v1.sendSourcesTo(NativeTraceBenchmark::consume);
        v2.sendSourcesTo(NativeTraceBenchmark::consume);
        mix = new ArrayList<>();
        CheckerFixtures.all().forEach(f -> {
            if (f.getDifference() == null) {
                mix.add(f.getSegment());
            }
        });
        wide = wideSegment(40, 8, 12);
        if ("warm".equals(aliasCache)) {
            // The rows come from a side of their own, so neither measured side starts with state the other lacks.
            final CheckerV1ListenerSide rowSource = new CheckerV1ListenerSide(profile, null);
            for (final SegmentObject segment : mix) {
                rowSource.analyzeForAliasSetups(segment);
            }
            for (final SegmentObject segment : peerSegments(wide)) {
                rowSource.analyzeForAliasSetups(segment);
            }
            final List<NetworkAddressAlias> rows = NativeTraceComparisonTest.aliasRows(rowSource.aliasSetups());
            v1.loadAliasRows(rows);
            v2.loadAliasRows(rows);
        }
    }

    private static void consume(final ISource source) {
        final Blackhole blackhole = SINK.get();
        if (blackhole != null) {
            blackhole.consume(source);
        }
    }

    /**
     * One benchmark thread: its place in the fixture mix, and its blackhole, which the sides' sources go to.
     */
    @State(Scope.Thread)
    public static class BenchmarkThread {
        private int next;

        @Setup(Level.Iteration)
        public void bind(final Blackhole blackhole) {
            SINK.set(blackhole);
        }

        SegmentObject nextOf(final List<SegmentObject> segments) {
            final SegmentObject segment = segments.get(next);
            next = (next + 1) % segments.size();
            return segment;
        }
    }

    @Benchmark
    public void javaMix(final BenchmarkThread thread) {
        v1.analyzeQuietly(thread.nextOf(mix));
    }

    @Benchmark
    public void rulesMix(final BenchmarkThread thread) {
        v2.analyzeQuietly(thread.nextOf(mix));
    }

    @Benchmark
    public void javaWide(final BenchmarkThread thread) {
        v1.analyzeQuietly(wide);
    }

    @Benchmark
    public void rulesWide(final BenchmarkThread thread) {
        v2.analyzeQuietly(wide);
    }

    @Benchmark
    @Threads(4)
    public void javaConcurrent(final BenchmarkThread thread) {
        v1.analyzeQuietly(wide);
    }

    @Benchmark
    @Threads(4)
    public void rulesConcurrent(final BenchmarkThread thread) {
        v2.analyzeQuietly(wide);
    }

    /**
     * A segment of one entry span with several references, and exit and local spans with many tags, so the queued
     * build blocks and the tag views are measured at size.
     */
    static SegmentObject wideSegment(final int spans, final int references, final int tags) {
        final SegmentObject.Builder segment = SegmentObject.newBuilder().setService("wide").setServiceInstance("wide-1")
                                                           .setTraceId("trace-wide").setTraceSegmentId("seg-wide");
        final long start = 1_700_000_000_000L;
        for (int i = 0; i < spans; i++) {
            final SpanObject.Builder span = SpanObject.newBuilder().setSpanId(i).setParentSpanId(i == 0 ? -1 : 0)
                                                      .setOperationName("/op/" + i).setStartTime(start + i)
                                                      .setEndTime(start + i + 20).setComponentId(1);
            if (i == 0) {
                span.setSpanType(SpanType.Entry).setSpanLayer(SpanLayer.Http);
                for (int r = 0; r < references; r++) {
                    span.addRefs(SegmentReference.newBuilder().setRefType(RefType.CrossProcess)
                                                 .setTraceId("trace-wide").setParentTraceSegmentId("seg-p" + r)
                                                 .setParentSpanId(1).setParentService("caller-" + r)
                                                 .setParentServiceInstance("caller-" + r + "-1")
                                                 .setParentEndpoint("/call/" + r)
                                                 .setNetworkAddressUsedAtPeer("wide:8080"));
                }
            } else if (i % 2 == 0) {
                span.setSpanType(SpanType.Exit).setSpanLayer(SpanLayer.Http).setPeer("peer-" + i + ":80");
            } else {
                span.setSpanType(SpanType.Local).setSpanLayer(SpanLayer.Unknown);
            }
            for (int t = 0; t < tags; t++) {
                span.addTags(KeyStringValuePair.newBuilder().setKey("k" + t).setValue("v" + t));
            }
            segment.addSpans(span);
        }
        return segment.build();
    }

    /**
     * The segments the wide segment's exit peers would send as callees, each reached at the peer's address, so a warm
     * cache holds a row for every address the wide segment dials.
     */
    static List<SegmentObject> peerSegments(final SegmentObject wide) {
        final List<SegmentObject> segments = new ArrayList<>();
        for (final SpanObject exit : wide.getSpansList()) {
            if (exit.getSpanType() != SpanType.Exit) {
                continue;
            }
            final String service = exit.getPeer().substring(0, exit.getPeer().indexOf(':'));
            final SpanObject entry = SpanObject.newBuilder().setSpanId(0).setParentSpanId(-1)
                                               .setSpanType(SpanType.Entry).setSpanLayer(SpanLayer.Http)
                                               .setOperationName("/served").setStartTime(exit.getStartTime())
                                               .setEndTime(exit.getEndTime()).setComponentId(1)
                                               .addRefs(SegmentReference.newBuilder().setRefType(RefType.CrossProcess)
                                                                        .setTraceId(wide.getTraceId())
                                                                        .setParentTraceSegmentId(wide.getTraceSegmentId())
                                                                        .setParentSpanId(exit.getSpanId())
                                                                        .setParentService(wide.getService())
                                                                        .setParentServiceInstance(wide.getServiceInstance())
                                                                        .setParentEndpoint("/op/0")
                                                                        .setNetworkAddressUsedAtPeer(exit.getPeer()))
                                               .build();
            segments.add(SegmentObject.newBuilder().setService(service).setServiceInstance(service + "-1")
                                      .setTraceId(wide.getTraceId()).setTraceSegmentId("seg-" + service)
                                      .addSpans(entry).build());
        }
        return segments;
    }

    @Test
    void runBenchmark() throws Exception {
        // The forks take the test JVM's arguments except the coverage agent, which instruments the Java side only.
        final List<String> jvmArgs = new ArrayList<>();
        for (final String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!argument.contains("jacoco")) {
                jvmArgs.add(argument);
            }
        }
        new Runner(new OptionsBuilder().include(getClass().getSimpleName()).addProfiler(GCProfiler.class)
                                       .jvmArgs(jvmArgs.toArray(new String[0])).build())
            .run();
    }
}
