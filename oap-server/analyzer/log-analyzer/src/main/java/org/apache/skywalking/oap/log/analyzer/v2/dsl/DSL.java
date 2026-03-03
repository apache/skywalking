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
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

/**
 * DSL compiles a LAL (Log Analysis Language) expression string into a
 * {@link LalExpression} object and wraps it with runtime state management.
 *
 * <p>One DSL instance is created per LAL rule entry defined in a {@code .yaml}
 * config file under {@code lal/}. Instances are compiled once at startup and
 * reused for every incoming log.
 *
 * <p>Runtime (per-log execution):
 * <ol>
 *   <li>{@link #bind(ExecutionContext)} — stores the current execution context.</li>
 *   <li>{@link #evaluate()} — invokes the compiled {@link LalExpression#execute},
 *       passing the {@link FilterSpec} and {@link ExecutionContext} explicitly.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DSL {
    private final LalExpression expression;
    private final FilterSpec filterSpec;
    private ExecutionContext ctx;

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl) throws ModuleStartException {
        return of(moduleManager, config, dsl, null);
    }

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl,
                         final String extraLogType) throws ModuleStartException {
        try {
            final LALClassGenerator generator = new LALClassGenerator();
            generator.setExtraLogType(extraLogType);
            final LalExpression expression = generator.compile(dsl);
            final FilterSpec filterSpec = new FilterSpec(moduleManager, config);
            return new DSL(expression, filterSpec);
        } catch (Exception e) {
            throw new ModuleStartException(
                "Failed to compile LAL expression: " + dsl, e);
        }
    }

    public void bind(final ExecutionContext ctx) {
        this.ctx = ctx;
    }

    public void evaluate() {
        expression.execute(filterSpec, ctx);
    }
}
