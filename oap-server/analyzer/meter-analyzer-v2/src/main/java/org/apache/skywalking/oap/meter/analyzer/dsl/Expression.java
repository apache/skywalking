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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Same-FQCN replacement for upstream Expression.
 * Wraps a transpiled {@link MalExpression} (pure Java) instead of a Groovy DelegatingScript.
 * No ExpandoMetaClass, no propertyMissing(), no ThreadLocal sample repository.
 */
@Slf4j
@ToString(of = {"literal"})
public class Expression {

    private final String metricName;
    private final String literal;
    private final MalExpression expression;

    public Expression(final String metricName, final String literal, final MalExpression expression) {
        this.metricName = metricName;
        this.literal = literal;
        this.expression = expression;
    }

    /**
     * Parse the expression statically.
     *
     * @return Parsed context of the expression.
     */
    public ExpressionParsingContext parse() {
        try (ExpressionParsingContext ctx = ExpressionParsingContext.create()) {
            final Result r = run(ImmutableMap.of());
            if (!r.isSuccess() && r.isThrowable()) {
                throw new ExpressionParsingException(
                    "failed to parse expression: " + literal + ", error:" + r.getError());
            }
            if (log.isDebugEnabled()) {
                log.debug("\"{}\" is parsed", literal);
            }
            ctx.validate(literal);
            return ctx;
        }
    }

    /**
     * Run the expression with a data map.
     *
     * @param sampleFamilies a data map includes all of candidates to be analysis.
     * @return The result of execution.
     */
    public Result run(final Map<String, SampleFamily> sampleFamilies) {
        try {
            for (final SampleFamily s : sampleFamilies.values()) {
                if (s != SampleFamily.EMPTY) {
                    s.context.setMetricName(metricName);
                }
            }
            final SampleFamily sf = expression.run(sampleFamilies);
            if (sf == SampleFamily.EMPTY) {
                if (ExpressionParsingContext.get().isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("result of {} is empty by \"{}\"", sampleFamilies, literal);
                    }
                }
                return Result.fail("Parsed result is an EMPTY sample family");
            }
            return Result.success(sf);
        } catch (Throwable t) {
            log.error("failed to run \"{}\"", literal, t);
            return Result.fail(t);
        }
    }
}
