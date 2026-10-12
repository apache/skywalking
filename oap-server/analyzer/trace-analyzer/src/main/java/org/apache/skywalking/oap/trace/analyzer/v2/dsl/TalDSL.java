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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.concurrent.atomic.AtomicBoolean;
import javassist.ClassPool;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;

/**
 * One compiled TAL rule and the state it needs to run: the generated class, the spec it calls into, and the
 * input type it was typed against.
 *
 * <p>Compiled once at startup, or on a runtime-rule push, and reused for every span. Immutable and thread-safe;
 * all per-span state is the {@link ExecutionContext} passed to {@link #evaluate}.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TalDSL {
    @Getter
    private final String ruleName;
    @Getter
    private final TalExpression expression;
    private final FilterSpec filterSpec;

    /**
     * The type this rule's {@code span} root was typed against. The runtime skips a rule whose input type does
     * not match the object that arrived, which is how OTLP and Zipkin rules coexist in one layer.
     */
    @Getter
    private final Class<?> inputType;

    /**
     * Flipped by the first {@link #firstFailure()}, so a rule's defect is logged once rather than per span.
     */
    private final AtomicBoolean failureReported = new AtomicBoolean();

    /**
     * @return true on the first call only
     */
    public boolean firstFailure() {
        return failureReported.compareAndSet(false, true);
    }

    public static TalDSL of(final ModuleManager moduleManager,
                            final TraceAnalyzerModuleConfig config,
                            final String dsl,
                            final Class<?> inputType,
                            final Class<?> outputType,
                            final String ruleName,
                            final String metricPrefix,
                            final DslSourceRef sourceRef) throws ModuleStartException {
        return of(moduleManager, config, dsl, inputType, outputType, ruleName, metricPrefix, sourceRef,
            null, null);
    }

    /**
     * Runtime-rule overload: the generated class is defined in the supplied loader rather than the app loader,
     * so a pushed rule can be replaced without leaking the previous class.
     *
     * @param pool              a per-file pool already scoped to {@code targetClassLoader}, or null
     * @param targetClassLoader the loader to define the class in, or null for the app loader
     * @return the compiled rule
     * @throws ModuleStartException when the rule does not parse, validate or compile
     */
    public static TalDSL of(final ModuleManager moduleManager,
                            final TraceAnalyzerModuleConfig config,
                            final String dsl,
                            final Class<?> inputType,
                            final Class<?> outputType,
                            final String ruleName,
                            final String metricPrefix,
                            final DslSourceRef sourceRef,
                            final ClassPool pool,
                            final ClassLoader targetClassLoader) throws ModuleStartException {
        try {
            final TALClassGenerator generator = pool != null && targetClassLoader != null
                ? new TALClassGenerator(pool, targetClassLoader) : new TALClassGenerator();
            generator.setInputType(inputType);
            generator.setOutputType(outputType);
            generator.setClassNameHint(ruleName);
            generator.setSourceRef(sourceRef);
            generator.setMetricPrefix(metricPrefix);
            generator.setContent(dsl);

            final TalExpression expression = generator.compile(dsl);
            // Every metric the rule declares is installed before the rule can see a span, so the storage model
            // exists by the time a value is accepted.
            generator.createMetrics(
                moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class));

            return new TalDSL(ruleName, expression, new FilterSpec(moduleManager, config), inputType);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to compile TAL rule " + ruleName, e);
        }
    }

    /**
     * Run this rule against one span.
     *
     * @param ctx the span's state, already initialized
     */
    public void evaluate(final ExecutionContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("[TAL] rule={}, class={}, service={}, kind={}",
                ruleName, expression.getClass().getName(),
                ctx.getMeta().getService(), ctx.getMeta().getKind());
        }
        expression.execute(filterSpec, ctx);
    }
}
