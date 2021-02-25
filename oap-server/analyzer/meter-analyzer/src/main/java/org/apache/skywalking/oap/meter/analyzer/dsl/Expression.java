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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObjectSupport;
import groovy.util.DelegatingScript;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Expression is a reusable monadic container type which represents a DSL expression.
 */
@Slf4j
@ToString(of = {"literal"})
public class Expression {

    private final String literal;

    private final DelegatingScript expression;

    private final ThreadLocal<ImmutableMap<String, SampleFamily>> propertyRepository = new ThreadLocal<>();

    public Expression(final String literal, final DelegatingScript expression) {
        this.literal = literal;
        this.expression = expression;
        this.empower();
    }

    /**
     * Parse the expression statically.
     *
     * @return Parsed context of the expression.
     */
    public ExpressionParsingContext parse() {
        try (ExpressionParsingContext ctx = ExpressionParsingContext.create()) {
            Result r = run(ImmutableMap.of());
            if (!r.isSuccess() && r.isThrowable()) {
                throw new ExpressionParsingException("failed to parse expression: " + literal + ", error:" + r.getError());
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
    public Result run(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        propertyRepository.set(sampleFamilies);
        try {
            SampleFamily sf = (SampleFamily) expression.run();
            if (sf == SampleFamily.EMPTY) {
                if (!ExpressionParsingContext.get().isPresent()) {
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
        } finally {
            propertyRepository.remove();
        }
    }

    private void empower() {
        expression.setDelegate(new ExpressionDelegate(literal, propertyRepository));
        extendNumber(Number.class);
    }

    private void extendNumber(Class clazz) {
        ExpandoMetaClass expando = new ExpandoMetaClass(clazz, true, false);
        expando.registerInstanceMethod("plus", new NumberClosure(this, (n, s) -> s.plus(n)));
        expando.registerInstanceMethod("minus", new NumberClosure(this, (n, s) -> s.minus(n).negative()));
        expando.registerInstanceMethod("multiply", new NumberClosure(this, (n, s) -> s.multiply(n)));
        expando.registerInstanceMethod("div", new NumberClosure(this, (n, s) -> s.newValue(v -> n.doubleValue() / v)));
        expando.initialize();
    }

    @RequiredArgsConstructor
    @SuppressWarnings("unused") // used in MAL expressions
    public static class ExpressionDelegate extends GroovyObjectSupport {
        public static final DownsamplingType AVG = DownsamplingType.AVG;
        public static final DownsamplingType SUM = DownsamplingType.SUM;
        public static final DownsamplingType LATEST = DownsamplingType.LATEST;

        private final String literal;
        private final ThreadLocal<ImmutableMap<String, SampleFamily>> propertyRepository;

        public SampleFamily propertyMissing(String metricName) {
            ExpressionParsingContext.get().ifPresent(ctx -> {
                if (!ctx.samples.contains(metricName)) {
                    ctx.samples.add(metricName);
                }
            });
            ImmutableMap<String, SampleFamily> sampleFamilies = propertyRepository.get();
            if (sampleFamilies == null) {
                return SampleFamily.EMPTY;
            }
            if (sampleFamilies.containsKey(metricName)) {
                return sampleFamilies.get(metricName);
            }
            if (!ExpressionParsingContext.get().isPresent()) {
                log.warn("{} referred by \"{}\" doesn't exist in {}", metricName, literal, sampleFamilies.keySet());
            }
            return SampleFamily.EMPTY;
        }

        public Number time() {
            return Instant.now().getEpochSecond();
        }

    }
}
