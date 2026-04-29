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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import javassist.ClassPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALMetadataExtractor;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALScriptParser;

/**
 * DSL compiles MAL expression strings into {@link Expression} objects
 * using ANTLR4 parsing and Javassist bytecode generation.
 */
@Slf4j
public final class DSL {

    private static final MALClassGenerator GENERATOR = new MALClassGenerator();

    /**
     * Parse string literal to Expression object, which can be reused.
     *
     * @param metricName the name of metric defined in mal rule
     * @param expression string literal represents the DSL expression.
     * @return Expression object could be executed.
     */
    public static Expression parse(final String metricName, final String expression) {
        return parse(metricName, expression, null);
    }

    /**
     * Parse string literal to Expression object with YAML source info for
     * stack trace diagnostics.
     *
     * @param metricName the name of metric defined in mal rule
     * @param expression string literal represents the DSL expression.
     * @param yamlSource YAML source identifier (e.g., "spring-sleuth[3]"), or null.
     * @return Expression object could be executed.
     */
    public static Expression parse(final String metricName,
                                   final String expression,
                                   final String yamlSource) {
        return parse(metricName, expression, yamlSource, null, null);
    }

    /**
     * Runtime-rule overload: compile with a per-file {@link ClassPool} and target
     * {@link ClassLoader}. Every class generated for this expression — the main
     * {@code MalExpression} subclass plus any closure companions — is defined in the
     * supplied loader instead of the shared OAP app loader. The caller-supplied pool must
     * already be scoped to the loader via {@code appendClassPath(new LoaderClassPath(loader))}.
     *
     * <p>When {@code pool} and {@code targetClassLoader} are both null, this delegates to
     * the shared {@link #GENERATOR} singleton (startup path, unchanged). Passing null for
     * only one of the two is treated as "startup path" — there is no half-isolated mode.
     */
    /**
     * Extract compile-time {@link ExpressionMetadata} from a MAL expression string without
     * running Javassist codegen. Returns scope type, sample names, aggregation labels,
     * histogram flag + percentiles, and downsampling — the inputs the runtime-rule classifier
     * needs to derive the storage shape tuple {@code (functionName, scopeType)} for a metric
     * and decide FILTER_ONLY vs STRUCTURAL.
     *
     * <p>Throws {@link IllegalStateException} on parse failure — wraps the upstream ANTLR
     * error listener so callers have a single exception type to catch.
     */
    public static ExpressionMetadata extractMetadata(final String expression) {
        try {
            final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
            return MALMetadataExtractor.extractMetadata(ast);
        } catch (final Exception e) {
            throw new IllegalStateException(
                "Failed to parse MAL expression for metadata: " + expression, e);
        }
    }

    public static Expression parse(final String metricName,
                                   final String expression,
                                   final String yamlSource,
                                   final ClassPool pool,
                                   final ClassLoader targetClassLoader) {
        try {
            final MalExpression malExpr;
            if (pool != null && targetClassLoader != null) {
                // Per-file generator: one instance per compile is fine — it's just a thin
                // orchestrator over ClassPool. Prevents cross-contamination of classNameHint /
                // yamlSource state that the shared GENERATOR carries between calls.
                final MALClassGenerator perFile = new MALClassGenerator(pool, targetClassLoader);
                perFile.setYamlSource(yamlSource);
                malExpr = perFile.compile(metricName, expression);
            } else {
                GENERATOR.setYamlSource(yamlSource);
                malExpr = GENERATOR.compile(metricName, expression);
            }
            return new Expression(metricName, expression, malExpr);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to compile MAL expression for metric: " + metricName
                    + ", expression: " + expression, e);
        }
    }
}
