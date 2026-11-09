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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeRuleRunner;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;

/**
 * The TAL native analysis: the native rule runner over the shipped rule files, in the order the shipped
 * {@code application.yml} lists them, compiled against this side's collaborators.
 */
final class CheckerV2RuleSide extends CheckerSide {
    private static final Pattern BUILD_METHOD = Pattern.compile("build_\\d+");

    private final SwNativeRuleRunner runner;
    private final List<TalDSL> rules;
    private final Map<String, String> ruleOfClass = new HashMap<>();

    CheckerV2RuleSide(final CheckerProfile profile, final CheckerLogCapture capture) throws Exception {
        super(profile, capture);
        final TraceAnalyzerModuleConfig config = new TraceAnalyzerModuleConfig();
        config.setTalPath("tal");
        config.setTalFiles("");
        config.setNativeTalFiles(CheckerApplicationYml.setting("trace-analyzer", "nativeTalFiles"));
        final SpanFilterListener.Factory factory = new SpanFilterListener.Factory(moduleManager, config);
        runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        rules = factory.compileNativeRules();
        rules.forEach(rule -> ruleOfClass.put(rule.getExpression().getClass().getName(), rule.getRuleName()));
        final Field expression = TalDSL.class.getDeclaredField("expression");
        expression.setAccessible(true);
        if (profile.isInstrumented()) {
            for (final TalDSL rule : rules) {
                expression.set(rule, indexing(rule.getExpression()));
            }
        }
        runner.loadRules(rules);
        log.setClassifier(this::classify);
    }

    /**
     * The compiled rule, whose span and first runs execute with the bound span's wire index set; the build runs, whose
     * span the Java builders do not know, without.
     */
    private static TalExpression indexing(final TalExpression compiled) {
        return (TalExpression) Proxy.newProxyInstance(compiled.getClass().getClassLoader(),
            new Class<?>[] {TalExpression.class}, (proxy, method, args) -> {
                final String name = method.getName();
                if (("execute".equals(name) || "executeFirst".equals(name)) && args != null
                    && args[1] instanceof ExecutionContext) {
                    final ExecutionContext ctx = (ExecutionContext) args[1];
                    final int index = CheckerWireIndex.indexOf(
                        ctx.getNativeSegment().getSegmentObject().getSpansList(), ctx.getInput());
                    return CheckerWireIndex.invokeAt(index, method, compiled, args);
                }
                return CheckerWireIndex.invoke(method, compiled, args);
            });
    }

    /**
     * @return the names of the compiled rules, in run order
     */
    List<String> ruleNames() {
        final List<String> names = new ArrayList<>();
        rules.forEach(rule -> names.add(rule.getRuleName()));
        return names;
    }

    /**
     * The rule of the innermost generated frame, in the phase of its method; the runner's own work otherwise.
     */
    private CheckerFrameClassifier.CheckerAttribution classify(final StackTraceElement[] frames) {
        for (final StackTraceElement frame : frames) {
            final String rule = ruleOfClass.get(frame.getClassName());
            final String method = frame.getMethodName();
            if (rule != null) {
                final String phase;
                if ("execute".equals(method)) {
                    phase = "span";
                } else if ("executeFirst".equals(method)) {
                    phase = "first";
                } else if ("executeBuild".equals(method) || BUILD_METHOD.matcher(method).matches()) {
                    phase = "build";
                } else {
                    phase = "?" + method;
                }
                return new CheckerFrameClassifier.CheckerAttribution(phase, CheckerGroups.ofRule(rule), rule);
            }
            if (CheckerGroups.RUNNER.equals(frame.getClassName())) {
                final String phase = "send".equals(method) ? "span" : "segment";
                return new CheckerFrameClassifier.CheckerAttribution(phase, "segment", "SwNativeRuleRunner");
            }
        }
        return null;
    }

    /**
     * The runner writes its span-loop line for a span before running the rules on it, so the next rule run tells it.
     */
    @Override
    protected void beforeAnalyze() {
        CheckerWireIndex.onRun(log::resolveLoopLines);
    }

    @Override
    protected void afterAnalyze(final SegmentObject segment) {
    }

    @Override
    protected void doAnalyze(final SegmentObject segment) {
        runner.send(segment);
    }
}
