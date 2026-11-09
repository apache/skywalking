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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListenerFactory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ListenerManagerAssembly;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.TraceAnalyzer;

/**
 * The Java native analysis: {@code TraceAnalyzer}, one per segment, with the listener assembly the agent analyzer
 * used.
 */
final class CheckerV1ListenerSide extends CheckerSide {
    private final SegmentParserListenerManager listeners;

    private static final Set<String> PHASE_METHODS = Set.of(
        "parseSegment", "parseFirst", "parseEntry", "parseExit", "parseLocal", "build");

    CheckerV1ListenerSide(final CheckerProfile profile, final CheckerLogCapture capture) throws Exception {
        super(profile, capture);
        final SegmentParserListenerManager assembled = withRecordedStatusAnalysis(
            () -> ListenerManagerAssembly.listenerManager(moduleManager, analyzerConfig));
        this.listeners = new SegmentParserListenerManager();
        assembled.getSpanListenerFactories().forEach(
            factory -> listeners.add(profile.isInstrumented() ? indexing(factory) : factory));
        log.setClassifier(CheckerV1ListenerSide::classify);
    }

    /**
     * The group of the innermost listener, processor or GenAI analyzer frame, and the phase of the listener callback
     * below it; a line of {@code TraceAnalyzer} itself belongs to the span loop.
     */
    static CheckerFrameClassifier.CheckerAttribution classify(final StackTraceElement[] frames) {
        String group = null;
        String owner = null;
        for (final StackTraceElement frame : frames) {
            final String className = frame.getClassName();
            final String frameGroup = CheckerGroups.ofClass(className);
            if (group == null && frameGroup != null) {
                group = frameGroup;
                owner = className.substring(className.lastIndexOf('.') + 1);
            }
            final String method = CheckerGroups.methodOf(frame);
            if (frameGroup != null && className.startsWith(CheckerGroups.LISTENER) && !className.contains(".vservice.")
                && PHASE_METHODS.contains(method)) {
                return new CheckerFrameClassifier.CheckerAttribution(phaseOf(method), group, owner);
            }
            if (className.equals(CheckerGroups.TRACE_ANALYZER)) {
                return new CheckerFrameClassifier.CheckerAttribution(
                    "span", group == null ? "segment" : group, owner == null ? "TraceAnalyzer" : owner);
            }
        }
        return null;
    }

    /**
     * The factory, making listeners whose span callbacks run with the span's wire index set.
     */
    private static AnalysisListenerFactory indexing(final AnalysisListenerFactory factory) {
        return new AnalysisListenerFactory() {
            @Override
            public String[] requiredModules() {
                return factory.requiredModules();
            }

            @Override
            public AnalysisListener create(final ModuleManager moduleManager, final AnalyzerModuleConfig config) {
                final AnalysisListener listener = factory.create(moduleManager, config);
                final Set<Class<?>> interfaces = new LinkedHashSet<>();
                for (Class<?> c = listener.getClass(); c != null; c = c.getSuperclass()) {
                    interfaces.addAll(Set.of(c.getInterfaces()));
                }
                return (AnalysisListener) Proxy.newProxyInstance(listener.getClass().getClassLoader(),
                    interfaces.toArray(new Class<?>[0]), (proxy, method, args) -> {
                        if (args != null && args.length == 2 && args[0] instanceof SpanObject
                            && args[1] instanceof SegmentObject) {
                            final int index = CheckerWireIndex.indexOf(
                                ((SegmentObject) args[1]).getSpansList(), args[0]);
                            return CheckerWireIndex.invokeAt(index, method, listener, args);
                        }
                        return CheckerWireIndex.invoke(method, listener, args);
                    });
            }
        };
    }

    private static String phaseOf(final String method) {
        switch (method) {
            case "parseSegment":
                return "segment";
            case "parseFirst":
                return "first";
            case "build":
                return "build";
            default:
                return "span";
        }
    }

    @Override
    protected void beforeAnalyze() {
    }

    /**
     * {@code TraceAnalyzer} writes its span-loop line for each span of no known type, in wire order.
     */
    @Override
    protected void afterAnalyze(final SegmentObject segment) {
        final List<Integer> unknown = new ArrayList<>();
        for (int i = 0; i < segment.getSpansCount(); i++) {
            final SpanType type = segment.getSpans(i).getSpanType();
            if (type != SpanType.Entry && type != SpanType.Exit && type != SpanType.Local) {
                unknown.add(i);
            }
        }
        log.resolveLoopLinesInOrder(unknown);
    }

    @Override
    protected void doAnalyze(final SegmentObject segment) {
        new TraceAnalyzer(moduleManager, listeners, analyzerConfig).doAnalysis(segment);
    }
}
