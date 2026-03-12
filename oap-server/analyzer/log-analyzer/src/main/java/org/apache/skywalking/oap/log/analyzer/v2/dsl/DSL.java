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

package org.apache.skywalking.oap.log.analyzer.v2.dsl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

/**
 * DSL compiles a LAL (Log Analysis Language) expression string into a
 * {@link LalExpression} object and wraps it with runtime state management.
 *
 * <p>One DSL instance is created per LAL rule entry defined in a {@code .yaml}
 * config file under {@code lal/}. Instances are compiled once at startup and
 * reused for every incoming log. This class is immutable and thread-safe —
 * per-log state is passed as a parameter to {@link #evaluate(ExecutionContext)}.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DSL {
    private final String ruleName;
    private final LalExpression expression;
    private final FilterSpec filterSpec;

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl) throws ModuleStartException {
        return of(moduleManager, config, dsl, null, null, "unknown", null);
    }

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl,
                         final Class<?> inputType,
                         final Class<?> outputType,
                         final String ruleName) throws ModuleStartException {
        return of(moduleManager, config, dsl, inputType, outputType, ruleName, null);
    }

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl,
                         final Class<?> inputType,
                         final Class<?> outputType,
                         final String ruleName,
                         final String yamlSource) throws ModuleStartException {
        try {
            final LALClassGenerator generator = new LALClassGenerator();
            generator.setInputType(inputType);
            generator.setOutputType(outputType);
            generator.setClassNameHint(ruleName);
            generator.setYamlSource(yamlSource);
            final LalExpression expression = generator.compile(dsl);
            final FilterSpec filterSpec = new FilterSpec(moduleManager, config);
            return new DSL(ruleName, expression, filterSpec);
        } catch (Exception e) {
            throw new ModuleStartException(
                "Failed to compile LAL expression: " + dsl, e);
        }
    }

    public void evaluate(final ExecutionContext ctx) {
        if (log.isDebugEnabled()) {
            final LogMetadata metadata = ctx.metadata();
            log.debug("[LAL] rule={}, class={}, service={}, instance={}, endpoint={}",
                ruleName, expression.getClass().getName(),
                metadata.getService(), metadata.getServiceInstance(),
                metadata.getEndpoint());
        }
        expression.execute(filterSpec, ctx);
    }
}
